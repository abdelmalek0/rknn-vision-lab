#ifndef RK_YOLOV5_DEMO_YOLO_IMAGE_H
#define RK_YOLOV5_DEMO_YOLO_IMAGE_H

#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "rkyolo4j", ##__VA_ARGS__);
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "rkyolo4j", ##__VA_ARGS__);

int create(int im_height, int im_width, int im_channel, char *model_path);
void destroy();
bool run_model(char *inDataRaw, char *y0, char *y1, char *y2);
int post_process(float *grid0_buf, float *grid1_buf, float *grid2_buf,
                 int *ids, float *scores, float *boxes);
int colorConvertAndFlip(void *src, int srcFmt, void *dst, int dstFmt, 
                        int width, int height, int flip);

#endif
