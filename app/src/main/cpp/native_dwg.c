#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "dwg.h"
#include "dwg_api.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "BraginCADNative", __VA_ARGS__)
typedef struct { double *v; size_t n, cap; } Buffer;
typedef struct { const char *name; size_t count; } TypeCount;

static void push(Buffer *b, double value) {
  if (b->n == b->cap) { b->cap = b->cap ? b->cap * 2 : 8192; b->v = realloc(b->v, b->cap * sizeof(double)); }
  b->v[b->n++] = value;
}

static int structural_entity(enum DWG_OBJECT_TYPE type) {
  return type == DWG_TYPE_BLOCK || type == DWG_TYPE_ENDBLK || type == DWG_TYPE_SEQEND ||
         type == DWG_TYPE_VERTEX_2D || type == DWG_TYPE_VERTEX_3D ||
         type == DWG_TYPE_VERTEX_MESH || type == DWG_TYPE_VERTEX_PFACE ||
         type == DWG_TYPE_VERTEX_PFACE_FACE;
}

static int layer_index(const char *name, char **names, unsigned int count) {
  if (!name) return 0;
  for (unsigned int i = 0; i < count; i++) if (names[i] && !strcmp(name, names[i])) return (int)i;
  return 0;
}

JNIEXPORT jobjectArray JNICALL Java_com_bragincad_dwg_LibreDwgAdapter_nativeRead(JNIEnv *env, jobject self, jstring jpath) {
  (void)self;
  const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
  Dwg_Data dwg; memset(&dwg, 0, sizeof(dwg));
  int decode_error = dwg_read_file(path, &dwg);
  (*env)->ReleaseStringUTFChars(env, jpath, path);
  if (decode_error >= DWG_ERR_CRITICAL) {
    dwg_free(&dwg); jclass ex=(*env)->FindClass(env,"java/io/IOException");
    (*env)->ThrowNew(env,ex,"LibreDWG could not decode this DWG"); return NULL;
  }

  unsigned int layer_count = dwg_get_layer_count(&dwg);
  Dwg_Object_LAYER **layers = dwg_get_layers(&dwg);
  char **layer_names = calloc(layer_count ? layer_count : 1, sizeof(char*));
  jboolean *layer_visible = calloc(layer_count ? layer_count : 1, sizeof(jboolean));
  for (unsigned int i=0; i<layer_count; i++) {
    int err=0; char *name = layers && layers[i] ? dwg_obj_layer_get_name(layers[i], &err) : NULL;
    layer_names[i] = strdup(name ? name : "0");
    layer_visible[i] = (!layers[i] || (!layers[i]->off && !layers[i]->frozen)) ? JNI_TRUE : JNI_FALSE;
    if (name && dwg.header.version >= R_2007) free(name);
  }

  TypeCount *types=NULL; size_t type_n=0, type_cap=0;
  size_t supported=0, unsupported_graphics=0, graphical=0, service=0, structural=0;
  Buffer b={0}; for(int i=0;i<8;i++) push(&b,0);
  for (BITCODE_BL i=0;i<dwg.num_objects;i++) {
    Dwg_Object *o=&dwg.object[i]; const char *type_name=o->name ? o->name : (o->dxfname ? o->dxfname : "UNKNOWN");
    size_t t; for(t=0;t<type_n;t++) if(!strcmp(types[t].name,type_name)){types[t].count++;break;}
    if(t==type_n){if(type_n==type_cap){type_cap=type_cap?type_cap*2:64;types=realloc(types,type_cap*sizeof(TypeCount));}types[type_n++]=(TypeCount){type_name,1};}
    if (o->supertype == DWG_SUPERTYPE_OBJECT) { service++; continue; }
    if (o->supertype != DWG_SUPERTYPE_ENTITY || !o->tio.entity) continue;
    if (structural_entity(o->fixedtype)) { structural++; continue; }
    graphical++;
    int name_error=0; char *entity_layer=dwg_ent_get_layer_name(o->tio.entity,&name_error);
    int layer=layer_index(entity_layer,layer_names,layer_count);
    int rendered=1;
    switch(o->fixedtype) {
      case DWG_TYPE_LINE: { Dwg_Entity_LINE *e=o->tio.entity->tio.LINE;if(!e){rendered=0;break;}push(&b,1);push(&b,layer);push(&b,e->start.x);push(&b,e->start.y);push(&b,e->end.x);push(&b,e->end.y);break; }
      case DWG_TYPE_CIRCLE: { Dwg_Entity_CIRCLE *e=o->tio.entity->tio.CIRCLE;if(!e){rendered=0;break;}push(&b,2);push(&b,layer);push(&b,e->center.x);push(&b,e->center.y);push(&b,e->radius);break; }
      case DWG_TYPE_ARC: { Dwg_Entity_ARC *e=o->tio.entity->tio.ARC;if(!e){rendered=0;break;}push(&b,3);push(&b,layer);push(&b,e->center.x);push(&b,e->center.y);push(&b,e->radius);push(&b,e->start_angle);push(&b,e->end_angle);break; }
      case DWG_TYPE_LWPOLYLINE: { Dwg_Entity_LWPOLYLINE *e=o->tio.entity->tio.LWPOLYLINE;if(!e||!e->points){rendered=0;break;}push(&b,4);push(&b,layer);push(&b,e->num_points);push(&b,(e->flag&512)?1:0);for(BITCODE_BL k=0;k<e->num_points;k++){push(&b,e->points[k].x);push(&b,e->points[k].y);}break; }
      case DWG_TYPE_ELLIPSE: { Dwg_Entity_ELLIPSE *e=o->tio.entity->tio.ELLIPSE;if(!e){rendered=0;break;}push(&b,5);push(&b,layer);push(&b,e->center.x);push(&b,e->center.y);push(&b,e->sm_axis.x);push(&b,e->sm_axis.y);push(&b,e->axis_ratio);push(&b,e->start_angle);push(&b,e->end_angle);break; }
      default: rendered=0; break;
    }
    if (entity_layer && dwg.header.version >= R_2007 && strcmp(entity_layer,"0")) free(entity_layer);
    if(rendered)supported++;else unsupported_graphics++;
  }
  b.v[0]=1024;b.v[1]=supported;b.v[2]=unsupported_graphics;b.v[3]=dwg.num_objects;b.v[4]=graphical;b.v[5]=service;b.v[6]=structural;b.v[7]=dwg.header_vars.INSUNITS;

  jdoubleArray geometry=(*env)->NewDoubleArray(env,(jsize)b.n);(*env)->SetDoubleArrayRegion(env,geometry,0,(jsize)b.n,b.v);
  jclass string_class=(*env)->FindClass(env,"java/lang/String");
  jobjectArray names=(*env)->NewObjectArray(env,(jsize)layer_count,string_class,NULL);
  for(unsigned int i=0;i<layer_count;i++){jstring s=(*env)->NewStringUTF(env,layer_names[i]);(*env)->SetObjectArrayElement(env,names,i,s);(*env)->DeleteLocalRef(env,s);}
  jbooleanArray visibility=(*env)->NewBooleanArray(env,(jsize)layer_count);(*env)->SetBooleanArrayRegion(env,visibility,0,(jsize)layer_count,layer_visible);
  jobjectArray stats=(*env)->NewObjectArray(env,(jsize)type_n,string_class,NULL);
  for(size_t i=0;i<type_n;i++){char line[160];snprintf(line,sizeof(line),"%s=%zu",types[i].name,types[i].count);jstring s=(*env)->NewStringUTF(env,line);(*env)->SetObjectArrayElement(env,stats,(jsize)i,s);(*env)->DeleteLocalRef(env,s);}
  jclass object_class=(*env)->FindClass(env,"java/lang/Object");jobjectArray result=(*env)->NewObjectArray(env,4,object_class,NULL);
  (*env)->SetObjectArrayElement(env,result,0,geometry);(*env)->SetObjectArrayElement(env,result,1,names);(*env)->SetObjectArrayElement(env,result,2,visibility);(*env)->SetObjectArrayElement(env,result,3,stats);
  LOGI("decoded objects=%u graphical=%zu service=%zu structural=%zu supported=%zu unsupported_graphical=%zu layers=%u units=%u error=%d",dwg.num_objects,graphical,service,structural,supported,unsupported_graphics,layer_count,dwg.header_vars.INSUNITS,decode_error);
  for(unsigned int i=0;i<layer_count;i++)free(layer_names[i]);free(layer_names);free(layer_visible);free(layers);free(types);free(b.v);dwg_free(&dwg);return result;
}
