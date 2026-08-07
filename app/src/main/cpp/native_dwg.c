#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include "dwg.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "BraginCADNative", __VA_ARGS__)
typedef struct { double *v; size_t n, cap; } Buffer;
static void push(Buffer *b, double value) { if (b->n == b->cap) { b->cap = b->cap ? b->cap * 2 : 8192; b->v = realloc(b->v, b->cap * sizeof(double)); } b->v[b->n++] = value; }

JNIEXPORT jdoubleArray JNICALL Java_com_bragincad_dwg_LibreDwgAdapter_nativeRead(JNIEnv *env, jobject self, jstring jpath) {
  (void)self; const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
  Dwg_Data dwg; memset(&dwg, 0, sizeof(dwg)); dwg.opts = 0;
  int error = dwg_read_file(path, &dwg); (*env)->ReleaseStringUTFChars(env, jpath, path);
  if (error >= DWG_ERR_CRITICAL) { dwg_free(&dwg); jclass ex=(*env)->FindClass(env,"java/io/IOException"); (*env)->ThrowNew(env,ex,"LibreDWG could not decode this DWG"); return NULL; }
  Buffer b={0}; push(&b,1024); push(&b,0); push(&b,0); size_t supported=0, unsupported=0;
  for (BITCODE_BL i=0;i<dwg.num_objects;i++) {
    Dwg_Object *o=&dwg.object[i]; if (!o->tio.entity) continue;
    switch(o->fixedtype) {
      case DWG_TYPE_LINE: { Dwg_Entity_LINE *e=o->tio.entity->tio.LINE; if(!e)break; push(&b,1);push(&b,e->start.x);push(&b,e->start.y);push(&b,e->end.x);push(&b,e->end.y);supported++;break; }
      case DWG_TYPE_CIRCLE: { Dwg_Entity_CIRCLE *e=o->tio.entity->tio.CIRCLE;if(!e)break;push(&b,2);push(&b,e->center.x);push(&b,e->center.y);push(&b,e->radius);supported++;break; }
      case DWG_TYPE_ARC: { Dwg_Entity_ARC *e=o->tio.entity->tio.ARC;if(!e)break;push(&b,3);push(&b,e->center.x);push(&b,e->center.y);push(&b,e->radius);push(&b,e->start_angle);push(&b,e->end_angle);supported++;break; }
      case DWG_TYPE_LWPOLYLINE: { Dwg_Entity_LWPOLYLINE *e=o->tio.entity->tio.LWPOLYLINE;if(!e||!e->points)break;push(&b,4);push(&b,e->num_points);push(&b,(e->flag&512)?1:0);for(BITCODE_BL k=0;k<e->num_points;k++){push(&b,e->points[k].x);push(&b,e->points[k].y);}supported++;break; }
      case DWG_TYPE_ELLIPSE: { Dwg_Entity_ELLIPSE *e=o->tio.entity->tio.ELLIPSE;if(!e)break;push(&b,5);push(&b,e->center.x);push(&b,e->center.y);push(&b,e->sm_axis.x);push(&b,e->sm_axis.y);push(&b,e->axis_ratio);push(&b,e->start_angle);push(&b,e->end_angle);supported++;break; }
      default: unsupported++; break;
    }
  }
  b.v[1]=(double)supported;b.v[2]=(double)unsupported;LOGI("decoded objects=%u supported=%zu unsupported=%zu error=%d",dwg.num_objects,supported,unsupported,error);dwg_free(&dwg);
  jdoubleArray out=(*env)->NewDoubleArray(env,(jsize)b.n);if(out)(*env)->SetDoubleArrayRegion(env,out,0,(jsize)b.n,b.v);free(b.v);return out;
}

