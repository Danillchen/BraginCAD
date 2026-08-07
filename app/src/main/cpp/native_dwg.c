#include <jni.h>
#include <android/log.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <math.h>
#include <time.h>
#include "dwg.h"
#include "dwg_api.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "BraginCADNative", __VA_ARGS__)
typedef struct { double *v; size_t n, cap; } Buffer;
typedef struct { const char *name; size_t count; } TypeCount;
typedef struct { char **v; size_t n, cap; } StringBuffer;
typedef struct { double a,b,c,d,tx,ty; } Affine;
typedef struct { Dwg_Data *dwg; Buffer *out; StringBuffer *texts; int *layers; char **layer_names; unsigned int layer_count; unsigned char *path; size_t primitives,cycles,depth_stops; } RenderCtx;

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

static void count_type(TypeCount **types, size_t *n, size_t *cap, const char *name) {
  size_t i; for(i=0;i<*n;i++) if(!strcmp((*types)[i].name,name)){(*types)[i].count++;return;}
  if(*n==*cap){*cap=*cap?*cap*2:32;*types=realloc(*types,*cap*sizeof(TypeCount));}
  (*types)[(*n)++]=(TypeCount){name,1};
}

static size_t add_string(StringBuffer *b, const char *value) {
  if(b->n==b->cap){b->cap=b->cap?b->cap*2:1024;b->v=realloc(b->v,b->cap*sizeof(char*));}
  b->v[b->n]=strdup(value?value:"");return b->n++;
}

typedef struct { size_t definitions, anonymous, inserts, rotated, scaled, nonuniform, nested, dimension_blocks, cycles; int max_depth; } BlockStats;

static int block_depth(Dwg_Data *dwg,Dwg_Object *header,unsigned char *path,int depth,BlockStats *stats){
  if(!header||header->fixedtype!=DWG_TYPE_BLOCK_HEADER||!header->tio.object||!header->tio.object->tio.BLOCK_HEADER)return depth;
  if(depth>64)return depth;
  if(path[header->index]){stats->cycles++;return depth;}
  path[header->index]=1;int best=depth;
  for(Dwg_Object *child=get_first_owned_entity(header);child;child=get_next_owned_entity(header,child)){
    if(child->fixedtype!=DWG_TYPE_INSERT||!child->tio.entity||!child->tio.entity->tio.INSERT)continue;
    Dwg_Object *next=dwg_ref_object_silent(dwg,child->tio.entity->tio.INSERT->block_header);
    int d=block_depth(dwg,next,path,depth+1,stats);if(d>best)best=d;
  }
  path[header->index]=0;return best;
}

static Affine affine_mul(Affine p,Affine q){return (Affine){p.a*q.a+p.c*q.b,p.b*q.a+p.d*q.b,p.a*q.c+p.c*q.d,p.b*q.c+p.d*q.d,p.a*q.tx+p.c*q.ty+p.tx,p.b*q.tx+p.d*q.ty+p.ty};}
static void affine_point(Affine m,double x,double y,double *ox,double *oy){*ox=m.a*x+m.c*y+m.tx;*oy=m.b*x+m.d*y+m.ty;}
static Affine insert_affine(Affine parent,double x,double y,double sx,double sy,double rotation,double bx,double by){double co=cos(rotation),si=sin(rotation);Affine local={co*sx,si*sx,-si*sy,co*sy,x-co*sx*bx+si*sy*by,y-si*sx*bx-co*sy*by};return affine_mul(parent,local);}
static void emit_poly_sample(RenderCtx *ctx,int layer,Affine m,double cx,double cy,double ux,double uy,double vx,double vy,double start,double end,int segments){push(ctx->out,4);push(ctx->out,layer);push(ctx->out,segments+1);push(ctx->out,0);for(int i=0;i<=segments;i++){double t=start+(end-start)*i/segments,x,y;affine_point(m,cx+ux*cos(t)+vx*sin(t),cy+uy*cos(t)+vy*sin(t),&x,&y);push(ctx->out,x);push(ctx->out,y);}ctx->primitives++;}
static void contour_point(Buffer *b,Affine m,double x,double y){double px,py;affine_point(m,x,y,&px,&py);push(b,px);push(b,py);}
static void emit_hatch(RenderCtx *ctx,Dwg_Entity_HATCH *e,Affine m,int layer){if(!e||!e->paths||!e->num_paths)return;Buffer *contours=calloc(e->num_paths,sizeof(Buffer));size_t valid=0;for(BITCODE_BL i=0;i<e->num_paths;i++){Dwg_HATCH_Path *p=&e->paths[i];Buffer *cb=&contours[valid];if((p->flag&2)&&p->polyline_paths){for(BITCODE_BL j=0;j<p->num_segs_or_paths;j++)contour_point(cb,m,p->polyline_paths[j].point.x,p->polyline_paths[j].point.y);}else if(p->segs){for(BITCODE_BL j=0;j<p->num_segs_or_paths;j++){Dwg_HATCH_PathSeg *s=&p->segs[j];if(s->curve_type==1){if(!cb->n)contour_point(cb,m,s->first_endpoint.x,s->first_endpoint.y);contour_point(cb,m,s->second_endpoint.x,s->second_endpoint.y);}else if(s->curve_type==2){double span=s->end_angle-s->start_angle;if(s->is_ccw&&span<0)span+=2*M_PI;if(!s->is_ccw&&span>0)span-=2*M_PI;for(int k=0;k<=16;k++){double a=s->start_angle+span*k/16.0;contour_point(cb,m,s->center.x+s->radius*cos(a),s->center.y+s->radius*sin(a));}}else if(s->curve_type==3){double span=s->end_angle-s->start_angle;if(s->is_ccw&&span<0)span+=2*M_PI;if(!s->is_ccw&&span>0)span-=2*M_PI;for(int k=0;k<=16;k++){double a=s->start_angle+span*k/16.0;double ux=s->endpoint.x,uy=s->endpoint.y,vx=-uy*s->minor_major_ratio,vy=ux*s->minor_major_ratio;contour_point(cb,m,s->center.x+ux*cos(a)+vx*sin(a),s->center.y+uy*cos(a)+vy*sin(a));}}else if(s->curve_type==4&&s->control_points){for(BITCODE_BL k=0;k<s->num_control_points;k++)contour_point(cb,m,s->control_points[k].point.x,s->control_points[k].point.y);}}}if(cb->n>=6)valid++;else{free(cb->v);cb->v=NULL;cb->n=0;}}
  if(!valid){free(contours);return;}push(ctx->out,8);push(ctx->out,layer);push(ctx->out,e->is_solid_fill?1:0);push(ctx->out,valid);for(size_t i=0;i<valid;i++){push(ctx->out,contours[i].n/2);for(size_t j=0;j<contours[i].n;j++)push(ctx->out,contours[i].v[j]);free(contours[i].v);}push(ctx->out,e->is_solid_fill?0:e->num_deflines);for(BITCODE_BS i=0;!e->is_solid_fill&&i<e->num_deflines;i++){Dwg_HATCH_DefLine *d=&e->deflines[i];double px,py,qx,qy,ox,oy;affine_point(m,d->pt0.x,d->pt0.y,&px,&py);affine_point(m,d->pt0.x+cos(d->angle),d->pt0.y+sin(d->angle),&qx,&qy);affine_point(m,d->pt0.x+d->offset.x,d->pt0.y+d->offset.y,&ox,&oy);push(ctx->out,px);push(ctx->out,py);push(ctx->out,qx-px);push(ctx->out,qy-py);push(ctx->out,ox-px);push(ctx->out,oy-py);}free(contours);ctx->primitives++;}
static int effective_layer(RenderCtx *ctx,Dwg_Object *o,int inherited){int own=ctx->layers[o->index];return own>=0&&strcmp(ctx->layer_names[own],"0")?own:inherited;}
static void emit_entity(RenderCtx *ctx,Dwg_Object *o,Affine m,int inherited,int depth);
static void emit_block(RenderCtx *ctx,Dwg_Object *header,Affine m,int inherited,int depth){
  if(!header||header->fixedtype!=DWG_TYPE_BLOCK_HEADER||!header->tio.object||!header->tio.object->tio.BLOCK_HEADER)return;
  if(depth>32){ctx->depth_stops++;return;}if(ctx->path[header->index]){ctx->cycles++;return;}ctx->path[header->index]=1;
  for(Dwg_Object *child=get_first_owned_entity(header);child;child=get_next_owned_entity(header,child))emit_entity(ctx,child,m,inherited,depth);
  ctx->path[header->index]=0;
}
static void emit_text(RenderCtx *ctx,Dwg_Object *o,Affine m,int layer,const char *name,const char *field,double x,double y,double height,double rotation,int horiz,int vert,double width,int multiline,double box,double spacing){
  char *text=NULL;int isnew=0;if(!dwg_dynapi_entity_utf8text(o->tio.entity->tio.UNUSED,name,field,&text,&isnew,NULL)||!text)return;
  double px,py;affine_point(m,x,y,&px,&py);double xx=m.a*cos(rotation)+m.c*sin(rotation),xy=m.b*cos(rotation)+m.d*sin(rotation),yx=-m.a*sin(rotation)+m.c*cos(rotation),yy=-m.b*sin(rotation)+m.d*cos(rotation);double xs=hypot(xx,xy),ys=hypot(yx,yy);size_t id=add_string(ctx->texts,text);
  push(ctx->out,multiline?7:6);push(ctx->out,layer);push(ctx->out,px);push(ctx->out,py);push(ctx->out,height*ys);push(ctx->out,atan2(xy,xx));push(ctx->out,id);push(ctx->out,horiz);if(!multiline){push(ctx->out,vert);push(ctx->out,width*(ys>1e-12?xs/ys:1));}else{push(ctx->out,box*xs);push(ctx->out,spacing);}ctx->primitives++;if(isnew)free(text);
}
static void emit_entity(RenderCtx *ctx,Dwg_Object *o,Affine m,int inherited,int depth){
  if(!o||o->supertype!=DWG_SUPERTYPE_ENTITY||!o->tio.entity||o->tio.entity->invisible)return;int layer=effective_layer(ctx,o,inherited);double x1,y1,x2,y2;
  switch(o->fixedtype){
    case DWG_TYPE_LINE:{Dwg_Entity_LINE *e=o->tio.entity->tio.LINE;affine_point(m,e->start.x,e->start.y,&x1,&y1);affine_point(m,e->end.x,e->end.y,&x2,&y2);push(ctx->out,1);push(ctx->out,layer);push(ctx->out,x1);push(ctx->out,y1);push(ctx->out,x2);push(ctx->out,y2);ctx->primitives++;break;}
    case DWG_TYPE_CIRCLE:{Dwg_Entity_CIRCLE *e=o->tio.entity->tio.CIRCLE;double sx=hypot(m.a,m.b),sy=hypot(m.c,m.d),dot=m.a*m.c+m.b*m.d;if(fabs(sx-sy)<1e-9&&fabs(dot)<1e-9){affine_point(m,e->center.x,e->center.y,&x1,&y1);push(ctx->out,2);push(ctx->out,layer);push(ctx->out,x1);push(ctx->out,y1);push(ctx->out,e->radius*sx);ctx->primitives++;}else emit_poly_sample(ctx,layer,m,e->center.x,e->center.y,e->radius,0,0,e->radius,0,2*M_PI,48);break;}
    case DWG_TYPE_ARC:{Dwg_Entity_ARC *e=o->tio.entity->tio.ARC;double span=e->end_angle-e->start_angle;if(span<=0)span+=2*M_PI;emit_poly_sample(ctx,layer,m,e->center.x,e->center.y,e->radius,0,0,e->radius,e->start_angle,e->start_angle+span,24);break;}
    case DWG_TYPE_LWPOLYLINE:{Dwg_Entity_LWPOLYLINE *e=o->tio.entity->tio.LWPOLYLINE;if(!e||!e->points)break;push(ctx->out,4);push(ctx->out,layer);push(ctx->out,e->num_points);push(ctx->out,(e->flag&512)?1:0);for(BITCODE_BL k=0;k<e->num_points;k++){affine_point(m,e->points[k].x,e->points[k].y,&x1,&y1);push(ctx->out,x1);push(ctx->out,y1);}ctx->primitives++;break;}
    case DWG_TYPE_ELLIPSE:{Dwg_Entity_ELLIPSE *e=o->tio.entity->tio.ELLIPSE;double span=e->end_angle-e->start_angle;if(span<=0)span+=2*M_PI;emit_poly_sample(ctx,layer,m,e->center.x,e->center.y,e->sm_axis.x,e->sm_axis.y,-e->sm_axis.y*e->axis_ratio,e->sm_axis.x*e->axis_ratio,e->start_angle,e->start_angle+span,32);break;}
    case DWG_TYPE_HATCH:emit_hatch(ctx,o->tio.entity->tio.HATCH,m,layer);break;
    case DWG_TYPE_TEXT:{Dwg_Entity_TEXT *e=o->tio.entity->tio.TEXT;double px=e->horiz_alignment||e->vert_alignment?e->alignment_pt.x:e->ins_pt.x,py=e->horiz_alignment||e->vert_alignment?e->alignment_pt.y:e->ins_pt.y;emit_text(ctx,o,m,layer,"TEXT","text_value",px,py,e->height,e->rotation,e->horiz_alignment,e->vert_alignment,e->width_factor,0,0,1);break;}
    case DWG_TYPE_MTEXT:{Dwg_Entity_MTEXT *e=o->tio.entity->tio.MTEXT;emit_text(ctx,o,m,layer,"MTEXT","text",e->ins_pt.x,e->ins_pt.y,e->text_height,atan2(e->x_axis_dir.y,e->x_axis_dir.x),e->attachment,0,1,1,e->rect_width,e->linespace_factor);break;}
    case DWG_TYPE_ATTRIB:{Dwg_Entity_ATTRIB *e=o->tio.entity->tio.ATTRIB;if(!(e->flags&1)){double px=e->horiz_alignment||e->vert_alignment?e->alignment_pt.x:e->ins_pt.x,py=e->horiz_alignment||e->vert_alignment?e->alignment_pt.y:e->ins_pt.y;emit_text(ctx,o,m,layer,"ATTRIB","text_value",px,py,e->height,e->rotation,e->horiz_alignment,e->vert_alignment,e->width_factor,0,0,1);}break;}
    case DWG_TYPE_INSERT:{Dwg_Entity_INSERT *e=o->tio.entity->tio.INSERT;Dwg_Object *h=dwg_ref_object_silent(ctx->dwg,e->block_header);if(!h||!h->tio.object||!h->tio.object->tio.BLOCK_HEADER)break;Dwg_Object_BLOCK_HEADER *bh=h->tio.object->tio.BLOCK_HEADER;Affine child=insert_affine(m,e->ins_pt.x,e->ins_pt.y,e->scale.x,e->scale.y,e->rotation,bh->base_pt.x,bh->base_pt.y);emit_block(ctx,h,child,layer,depth+1);if(e->has_attribs)for(Dwg_Object *a=get_first_owned_subentity(o);a;a=get_next_owned_subentity(o,a))if(a->fixedtype==DWG_TYPE_ATTRIB)emit_entity(ctx,a,m,layer,depth);break;}
    case DWG_TYPE_DIMENSION_LINEAR:case DWG_TYPE_DIMENSION_ALIGNED:{Dwg_DIMENSION_common *e=o->tio.entity->tio.DIMENSION_common;Dwg_Object *h=dwg_ref_object_silent(ctx->dwg,e->block);if(!h||!h->tio.object||!h->tio.object->tio.BLOCK_HEADER)break;Dwg_Object_BLOCK_HEADER *bh=h->tio.object->tio.BLOCK_HEADER;Affine child=insert_affine(m,e->clone_ins_pt.x,e->clone_ins_pt.y,e->ins_scale.x,e->ins_scale.y,e->ins_rotation,bh->base_pt.x,bh->base_pt.y);emit_block(ctx,h,child,layer,depth+1);break;}
    default:break;
  }
}

JNIEXPORT jobjectArray JNICALL Java_com_bragincad_dwg_LibreDwgAdapter_nativeRead(JNIEnv *env, jobject self, jstring jpath) {
  (void)self;
  struct timespec started,decoded,built;clock_gettime(CLOCK_MONOTONIC,&started);
  const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
  Dwg_Data dwg; memset(&dwg, 0, sizeof(dwg));
  int decode_error = dwg_read_file(path, &dwg);
  clock_gettime(CLOCK_MONOTONIC,&decoded);
  (*env)->ReleaseStringUTFChars(env, jpath, path);
  if (decode_error >= DWG_ERR_CRITICAL) {
    dwg_free(&dwg); jclass ex=(*env)->FindClass(env,"java/io/IOException");
    (*env)->ThrowNew(env,ex,"LibreDWG could not decode this DWG"); return NULL;
  }

  unsigned int layer_count = dwg_get_layer_count(&dwg);
  Dwg_Object_LAYER **layers = dwg_get_layers(&dwg);
  char **layer_names = calloc(layer_count ? layer_count : 1, sizeof(char*));
  jboolean *layer_visible = calloc(layer_count ? layer_count : 1, sizeof(jboolean));
  size_t *point_layers = calloc(layer_count ? layer_count : 1, sizeof(size_t));
  double point_min_x=INFINITY,point_min_y=INFINITY,point_max_x=-INFINITY,point_max_y=-INFINITY;
  for (unsigned int i=0; i<layer_count; i++) {
    int err=0; char *name = layers && layers[i] ? dwg_obj_layer_get_name(layers[i], &err) : NULL;
    layer_names[i] = strdup(name ? name : "0");
    layer_visible[i] = (!layers[i] || (!layers[i]->off && !layers[i]->frozen)) ? JNI_TRUE : JNI_FALSE;
    if (name && dwg.header.version >= R_2007) free(name);
  }

  BlockStats block_stats={0};unsigned char *block_path=calloc(dwg.num_objects?dwg.num_objects:1,1);
  for(BITCODE_BL i=0;i<dwg.num_objects;i++){
    Dwg_Object *o=&dwg.object[i];
    if(o->fixedtype==DWG_TYPE_BLOCK_HEADER&&o->tio.object&&o->tio.object->tio.BLOCK_HEADER){block_stats.definitions++;if(o->tio.object->tio.BLOCK_HEADER->anonymous)block_stats.anonymous++;int d=block_depth(&dwg,o,block_path,0,&block_stats);if(d>block_stats.max_depth)block_stats.max_depth=d;}
    if(o->fixedtype==DWG_TYPE_INSERT&&o->tio.entity&&o->tio.entity->tio.INSERT){Dwg_Entity_INSERT *e=o->tio.entity->tio.INSERT;block_stats.inserts++;if(fabs(e->rotation)>1e-10)block_stats.rotated++;if(fabs(e->scale.x-1)>1e-10||fabs(e->scale.y-1)>1e-10||fabs(e->scale.z-1)>1e-10)block_stats.scaled++;if(fabs(e->scale.x-e->scale.y)>1e-10)block_stats.nonuniform++;if(o->tio.entity->entmode==3)block_stats.nested++;}
    if((o->fixedtype==DWG_TYPE_DIMENSION_LINEAR||o->fixedtype==DWG_TYPE_DIMENSION_ALIGNED)&&o->tio.entity&&o->tio.entity->tio.DIMENSION_common&&dwg_ref_object_silent(&dwg,o->tio.entity->tio.DIMENSION_common->block))block_stats.dimension_blocks++;
  }
  free(block_path);
  LOGI("block_profile definitions=%zu anonymous=%zu inserts=%zu rotated=%zu scaled=%zu nonuniform=%zu nested=%zu max_depth=%d cycles=%zu dimension_blocks=%zu",block_stats.definitions,block_stats.anonymous,block_stats.inserts,block_stats.rotated,block_stats.scaled,block_stats.nonuniform,block_stats.nested,block_stats.max_depth,block_stats.cycles,block_stats.dimension_blocks);

  TypeCount *types=NULL,*unsupported_types=NULL,*hatch_patterns=NULL; size_t type_n=0,type_cap=0,unsupported_n=0,unsupported_cap=0,hatch_pattern_n=0,hatch_pattern_cap=0;
  size_t supported=0, unsupported_graphics=0, graphical=0, service=0, structural=0,invisible_graphical=0;size_t hatch_solid=0,hatch_pattern=0,hatch_paths=0,hatch_associative=0,hatch_poly_paths=0,hatch_line_edges=0,hatch_arc_edges=0,hatch_ellipse_edges=0,hatch_spline_edges=0,spline_ctrl=0,spline_fit=0,wipeout_clips=0,viewport_model=0,viewport_paper=0,viewport_block=0;int spline_max_degree=0;StringBuffer texts={0};int *object_layers=malloc((dwg.num_objects?dwg.num_objects:1)*sizeof(int));size_t *style_text=calloc(dwg.num_objects?dwg.num_objects:1,sizeof(size_t)),*style_mtext=calloc(dwg.num_objects?dwg.num_objects:1,sizeof(size_t)),*style_attrib=calloc(dwg.num_objects?dwg.num_objects:1,sizeof(size_t));for(BITCODE_BL i=0;i<dwg.num_objects;i++)object_layers[i]=0;
  Buffer b={0}; for(int i=0;i<10;i++) push(&b,0);
  for (BITCODE_BL i=0;i<dwg.num_objects;i++) {
    Dwg_Object *o=&dwg.object[i];const char *unknown_class=NULL;if(o->fixedtype==DWG_TYPE_UNKNOWN_ENT){if(o->klass&&o->klass->dxfname)unknown_class=o->klass->dxfname;else if(o->type>=500&&(unsigned)(o->type-500)<dwg.num_classes)unknown_class=dwg.dwg_class[o->type-500].dxfname;else if(o->dxfname&&strcmp(o->dxfname,"UNKNOWN_ENT"))unknown_class=o->dxfname;}const char *type_name=unknown_class?unknown_class:(o->name ? o->name : (o->dxfname ? o->dxfname : "UNKNOWN"));
    count_type(&types,&type_n,&type_cap,type_name);
    if (o->supertype == DWG_SUPERTYPE_OBJECT) { service++; continue; }
    if (o->supertype != DWG_SUPERTYPE_ENTITY || !o->tio.entity) continue;
    if (structural_entity(o->fixedtype)) { structural++; continue; }
    graphical++;
    Dwg_Object_Ref *style_ref=NULL;if(o->fixedtype==DWG_TYPE_TEXT)style_ref=o->tio.entity->tio.TEXT->style;else if(o->fixedtype==DWG_TYPE_MTEXT)style_ref=o->tio.entity->tio.MTEXT->style;else if(o->fixedtype==DWG_TYPE_ATTRIB)style_ref=o->tio.entity->tio.ATTRIB->style;if(style_ref){Dwg_Object *style_obj=dwg_ref_object_silent(&dwg,style_ref);if(style_obj&&style_obj->index<dwg.num_objects){if(o->fixedtype==DWG_TYPE_TEXT)style_text[style_obj->index]++;else if(o->fixedtype==DWG_TYPE_MTEXT)style_mtext[style_obj->index]++;else style_attrib[style_obj->index]++;}}
    int name_error=0; char *entity_layer=dwg_ent_get_layer_name(o->tio.entity,&name_error);
    int layer=layer_index(entity_layer,layer_names,layer_count);
    object_layers[o->index]=layer;int rendered=0;
    if(o->fixedtype==DWG_TYPE_POINT && o->tio.entity->tio.POINT){Dwg_Entity_POINT *p=o->tio.entity->tio.POINT;if((unsigned)layer<layer_count)point_layers[layer]++;if(p->x<point_min_x)point_min_x=p->x;if(p->y<point_min_y)point_min_y=p->y;if(p->x>point_max_x)point_max_x=p->x;if(p->y>point_max_y)point_max_y=p->y;}
    if(o->fixedtype==DWG_TYPE_HATCH&&o->tio.entity->tio.HATCH){Dwg_Entity_HATCH *e=o->tio.entity->tio.HATCH;if(e->is_solid_fill)hatch_solid++;else hatch_pattern++;if(e->is_associative)hatch_associative++;hatch_paths+=e->num_paths;count_type(&hatch_patterns,&hatch_pattern_n,&hatch_pattern_cap,e->name?e->name:"(none)");for(BITCODE_BL hp=0;hp<e->num_paths;hp++){Dwg_HATCH_Path *path=&e->paths[hp];if(path->flag&2)hatch_poly_paths++;else for(BITCODE_BL hs=0;hs<path->num_segs_or_paths;hs++)switch(path->segs[hs].curve_type){case 1:hatch_line_edges++;break;case 2:hatch_arc_edges++;break;case 3:hatch_ellipse_edges++;break;case 4:hatch_spline_edges++;break;}}}
    if(o->fixedtype==DWG_TYPE_SPLINE&&o->tio.entity->tio.SPLINE){Dwg_Entity_SPLINE *e=o->tio.entity->tio.SPLINE;spline_ctrl+=e->num_ctrl_pts;spline_fit+=e->num_fit_pts;if(e->degree>spline_max_degree)spline_max_degree=e->degree;}
    if(o->fixedtype==DWG_TYPE_WIPEOUT&&o->tio.entity->tio.WIPEOUT)wipeout_clips+=o->tio.entity->tio.WIPEOUT->num_clip_verts;
    if(o->fixedtype==DWG_TYPE_VIEWPORT){if(o->tio.entity->entmode==1)viewport_paper++;else if(o->tio.entity->entmode==2)viewport_model++;else viewport_block++;}
    switch(o->fixedtype) {
      case DWG_TYPE_LINE:case DWG_TYPE_CIRCLE:case DWG_TYPE_ARC:case DWG_TYPE_LWPOLYLINE:case DWG_TYPE_ELLIPSE:case DWG_TYPE_TEXT:case DWG_TYPE_MTEXT:case DWG_TYPE_INSERT:case DWG_TYPE_ATTRIB:case DWG_TYPE_HATCH:rendered=1;break;
      case DWG_TYPE_DIMENSION_LINEAR:case DWG_TYPE_DIMENSION_ALIGNED:rendered=dwg_ref_object_silent(&dwg,o->tio.entity->tio.DIMENSION_common->block)!=NULL;break;
      default:break;
    }
    if (entity_layer && dwg.header.version >= R_2007 && strcmp(entity_layer,"0")) free(entity_layer);
    if(rendered)supported++;else{unsupported_graphics++;count_type(&unsupported_types,&unsupported_n,&unsupported_cap,type_name);}
  }
  int defpoints=layer_index("Defpoints",layer_names,layer_count);if(dwg.header_vars.PDMODE==0&&defpoints>=0)invisible_graphical=point_layers[defpoints];
  Dwg_Object *model_header=dwg_ref_object_silent(&dwg,dwg.header_vars.BLOCK_RECORD_MSPACE);unsigned char *render_path=calloc(dwg.num_objects?dwg.num_objects:1,1);RenderCtx render={&dwg,&b,&texts,object_layers,layer_names,layer_count,render_path,0,0,0};emit_block(&render,model_header,(Affine){1,0,0,1,0,0},0,0);free(render_path);
  clock_gettime(CLOCK_MONOTONIC,&built);double decode_ms=(decoded.tv_sec-started.tv_sec)*1000.0+(decoded.tv_nsec-started.tv_nsec)/1e6;double build_ms=(built.tv_sec-decoded.tv_sec)*1000.0+(built.tv_nsec-decoded.tv_nsec)/1e6;
  b.v[0]=1024;b.v[1]=supported;b.v[2]=unsupported_graphics;b.v[3]=dwg.num_objects;b.v[4]=graphical;b.v[5]=service;b.v[6]=structural;b.v[7]=dwg.header_vars.INSUNITS;b.v[8]=graphical-invisible_graphical;b.v[9]=render.primitives;

  jdoubleArray geometry=(*env)->NewDoubleArray(env,(jsize)b.n);(*env)->SetDoubleArrayRegion(env,geometry,0,(jsize)b.n,b.v);
  jclass string_class=(*env)->FindClass(env,"java/lang/String");
  jobjectArray names=(*env)->NewObjectArray(env,(jsize)layer_count,string_class,NULL);
  for(unsigned int i=0;i<layer_count;i++){jstring s=(*env)->NewStringUTF(env,layer_names[i]);(*env)->SetObjectArrayElement(env,names,i,s);(*env)->DeleteLocalRef(env,s);}
  jbooleanArray visibility=(*env)->NewBooleanArray(env,(jsize)layer_count);(*env)->SetBooleanArrayRegion(env,visibility,0,(jsize)layer_count,layer_visible);
  jobjectArray stats=(*env)->NewObjectArray(env,(jsize)(type_n+unsupported_n),string_class,NULL);
  for(size_t i=0;i<type_n;i++){char line[160];snprintf(line,sizeof(line),"A|%s=%zu",types[i].name,types[i].count);jstring s=(*env)->NewStringUTF(env,line);(*env)->SetObjectArrayElement(env,stats,(jsize)i,s);(*env)->DeleteLocalRef(env,s);}
  for(size_t i=0;i<unsupported_n;i++){char line[160];snprintf(line,sizeof(line),"U|%s=%zu",unsupported_types[i].name,unsupported_types[i].count);jstring s=(*env)->NewStringUTF(env,line);(*env)->SetObjectArrayElement(env,stats,(jsize)(type_n+i),s);(*env)->DeleteLocalRef(env,s);}
  jobjectArray text_values=(*env)->NewObjectArray(env,(jsize)texts.n,string_class,NULL);
  for(size_t i=0;i<texts.n;i++){jstring s=(*env)->NewStringUTF(env,texts.v[i]);(*env)->SetObjectArrayElement(env,text_values,(jsize)i,s);(*env)->DeleteLocalRef(env,s);}
  jclass object_class=(*env)->FindClass(env,"java/lang/Object");jobjectArray result=(*env)->NewObjectArray(env,5,object_class,NULL);
  (*env)->SetObjectArrayElement(env,result,0,geometry);(*env)->SetObjectArrayElement(env,result,1,names);(*env)->SetObjectArrayElement(env,result,2,visibility);(*env)->SetObjectArrayElement(env,result,3,stats);(*env)->SetObjectArrayElement(env,result,4,text_values);
  LOGI("decoded objects=%u graphical=%zu visible_graphical=%zu service=%zu structural=%zu supported=%zu unsupported_graphical=%zu primitives=%zu block_cycles=%zu depth_stops=%zu layers=%u units=%u error=%d",dwg.num_objects,graphical,graphical-invisible_graphical,service,structural,supported,unsupported_graphics,render.primitives,render.cycles,render.depth_stops,layer_count,dwg.header_vars.INSUNITS,decode_error);
  LOGI("timing decode_ms=%.2f native_model_build_ms=%.2f",decode_ms,build_ms);
  LOGI("point_usage pdmode=%d pdsize=%.6g bounds=[%.6g,%.6g]-[%.6g,%.6g]",dwg.header_vars.PDMODE,dwg.header_vars.PDSIZE,point_min_x,point_min_y,point_max_x,point_max_y);
  LOGI("remaining_profile hatch_solid=%zu hatch_pattern=%zu hatch_paths=%zu spline_ctrl=%zu spline_fit=%zu spline_max_degree=%d wipeout_clip_vertices=%zu viewport_model=%zu viewport_paper=%zu viewport_block=%zu",hatch_solid,hatch_pattern,hatch_paths,spline_ctrl,spline_fit,spline_max_degree,wipeout_clips,viewport_model,viewport_paper,viewport_block);
  LOGI("hatch_profile associative=%zu polyline_paths=%zu line_edges=%zu arc_edges=%zu ellipse_edges=%zu spline_edges=%zu",hatch_associative,hatch_poly_paths,hatch_line_edges,hatch_arc_edges,hatch_ellipse_edges,hatch_spline_edges);for(size_t i=0;i<hatch_pattern_n;i++)LOGI("hatch_pattern name=%s count=%zu",hatch_patterns[i].name,hatch_patterns[i].count);
  for(BITCODE_BL i=0;i<dwg.num_objects;i++)if(dwg.object[i].fixedtype==DWG_TYPE_STYLE&&dwg.object[i].tio.object&&dwg.object[i].tio.object->tio.STYLE){Dwg_Object_STYLE *s=dwg.object[i].tio.object->tio.STYLE;int name_err=0;char *style_name=dwg_obj_table_get_name(&dwg.object[i],&name_err),*font=NULL,*bigfont=NULL;int font_new=0,bigfont_new=0;dwg_dynapi_entity_utf8text(s,"STYLE","font_file",&font,&font_new,NULL);dwg_dynapi_entity_utf8text(s,"STYLE","bigfont_file",&bigfont,&bigfont_new,NULL);const char *kind=(font&&strstr(font,".shx"))?"SHX":((font&&strstr(font,".ttf"))?"TTF":"unknown");LOGI("text_style name=%s font=%s bigfont=%s kind=%s fixed_height=%.6g width_factor=%.6g oblique=%.6g generation=%u text=%zu mtext=%zu attrib=%zu",style_name?style_name:"",font?font:"",bigfont?bigfont:"",kind,s->text_size,s->width_factor,s->oblique_angle,s->generation,style_text[i],style_mtext[i],style_attrib[i]);if(style_name&&dwg.header.version>=R_2007)free(style_name);if(font_new)free(font);if(bigfont_new)free(bigfont);}
  for(unsigned int i=0;i<layer_count;i++)if(point_layers[i])LOGI("point_layer name=%s count=%zu visible=%d",layer_names[i],point_layers[i],layer_visible[i]);
  for(unsigned int i=0;i<layer_count;i++)free(layer_names[i]);for(size_t i=0;i<texts.n;i++)free(texts.v[i]);free(texts.v);free(style_text);free(style_mtext);free(style_attrib);free(object_layers);free(point_layers);free(layer_names);free(layer_visible);free(layers);free(types);free(unsupported_types);free(hatch_patterns);free(b.v);dwg_free(&dwg);return result;
}
