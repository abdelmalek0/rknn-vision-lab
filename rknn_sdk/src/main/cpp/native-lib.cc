
#include <jni.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <pthread.h>
#include <sys/syscall.h>

#include <sched.h>

#include "yolo_image.h"
#include "rga/rga.h"

static char* jstringToChar(JNIEnv* env, jstring jstr) {
    char* rtn = NULL;
    jclass clsstring = env->FindClass("java/lang/String");
    jstring strencode = env->NewStringUTF("utf-8");
    jmethodID mid = env->GetMethodID(clsstring, "getBytes", "(Ljava/lang/String;)[B");
    jbyteArray barr = (jbyteArray) env->CallObjectMethod(jstr, mid, strencode);
    jsize alen = env->GetArrayLength(barr);
    jbyte* ba = env->GetByteArrayElements(barr, JNI_FALSE);

    if (alen > 0) {
        rtn = new char[alen + 1];
        memcpy(rtn, ba, alen);
        rtn[alen] = 0;
    }
    env->ReleaseByteArrayElements(barr, ba, 0);
    return rtn;
}


extern "C"
JNIEXPORT jint JNICALL Java_com_smartprintsksa_yolo_1detector_InferenceWrapper_native_1init_1yolo
  (JNIEnv *env, jobject obj, jint im_height, jint im_width, jint im_channel,
   jstring model_path)
{
	char *model_path_p = jstringToChar(env, model_path);
	return create(im_height, im_width, im_channel, model_path_p);
}


extern "C"
JNIEXPORT void JNICALL Java_com_smartprintsksa_yolo_1detector_InferenceWrapper_native_1de_1init_1yolo
		(JNIEnv *env, jobject obj) {
	destroy();

}

extern "C"
JNIEXPORT jint JNICALL Java_com_smartprintsksa_yolo_1detector_InferenceWrapper_native_1run_1yolo
  (JNIEnv *env, jobject obj, jbyteArray in,
   jbyteArray grid0Out, jbyteArray grid1Out, jbyteArray grid2Out) {


  	jboolean inputCopy = JNI_FALSE;
  	jbyte* const inData = env->GetByteArrayElements(in, &inputCopy);

 	jboolean outputCopy = JNI_FALSE;

  	jbyte* const y0 = env->GetByteArrayElements(grid0Out, &outputCopy);
	jbyte* const y1 = env->GetByteArrayElements(grid1Out, &outputCopy);
	jbyte* const y2 = env->GetByteArrayElements(grid2Out, &outputCopy);

	run_model((char *)inData, (char *)y0, (char *)y1, (char *)y2);

	env->ReleaseByteArrayElements(in, inData, JNI_ABORT); // do not copy data back
	env->ReleaseByteArrayElements(grid0Out, y0, 0); // 0 to copy data back
	env->ReleaseByteArrayElements(grid1Out, y1, 0);
	env->ReleaseByteArrayElements(grid2Out, y2, 0);

	return 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_smartprintsksa_yolo_1detector_InferenceWrapper_native_1post_1process_1yolo(JNIEnv *env, jobject thiz,
																			   jbyteArray grid0_out,
																			   jbyteArray grid1_out,
																			   jbyteArray grid2_out,
																			   jintArray ids,
																			   jfloatArray scores,
																			   jfloatArray boxes) {
	jint detect_counts;
	jboolean inputCopy = JNI_FALSE;
	jbyte* const grid0_buf = env->GetByteArrayElements(grid0_out, &inputCopy);
	jbyte* const grid1_buf = env->GetByteArrayElements(grid1_out, &inputCopy);
	jbyte* const grid2_buf = env->GetByteArrayElements(grid2_out, &inputCopy);

	jboolean outputCopy = JNI_FALSE;

	jint*   const y0 = env->GetIntArrayElements(ids, &outputCopy);
	jfloat* const y1 = env->GetFloatArrayElements(scores, &outputCopy);
	jfloat* const y2 = env->GetFloatArrayElements(boxes, &outputCopy);

	detect_counts = post_process((float *)grid0_buf, (float *)grid1_buf, (float *)grid2_buf,
									  (int *)y0, (float *)y1, (float *)y2);

	env->ReleaseByteArrayElements(grid0_out, grid0_buf, JNI_ABORT);
	env->ReleaseByteArrayElements(grid1_out, grid1_buf, JNI_ABORT);
	env->ReleaseByteArrayElements(grid2_out, grid2_buf, JNI_ABORT);
	env->ReleaseIntArrayElements(ids, y0, 0);
	env->ReleaseFloatArrayElements(scores, y1, 0);
	env->ReleaseFloatArrayElements(boxes, y2, 0);

	return detect_counts;
}
