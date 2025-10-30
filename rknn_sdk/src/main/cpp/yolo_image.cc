#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <algorithm>
#include <sys/time.h>

#include "rknn_api.h"
#include "yolo_image.h"
#include "rga/rga.h"
#include "rga/im2d.h"
#include "post_process.h"

#define ZERO_COPY 1
#define DO_NOT_FLIP -1

// Global state for RKNN context and initialization
static rknn_context ctx = 0;
static bool created = false;

// Image dimensions: original input and model input
static int img_width = 0, img_height = 0;
static int m_in_width = 0, m_in_height = 0, m_in_channel = 0;
static float scale_w = 0.0f, scale_h = 0.0f;

// RKNN model configuration
static const uint32_t n_input = 1, n_output = 3;
static rknn_tensor_attr input_attrs[n_input], output_attrs[n_output];
static rknn_tensor_mem *input_mems[n_input], *output_mems[n_output];
static rga_buffer_t g_rga_src, g_rga_dst;
static std::vector<float> out_scales;
static std::vector<int32_t> out_zps;

// Helper function to clean up allocated memory
static void cleanup_memory() {
    for (int i = 0; i < n_input; ++i) {
        if (input_mems[i]) {
            rknn_destroy_mem(ctx, input_mems[i]);
            input_mems[i] = nullptr;
        }
    }
    for (int i = 0; i < n_output; ++i) {
        if (output_mems[i]) {
            rknn_destroy_mem(ctx, output_mems[i]);
            output_mems[i] = nullptr;
        }
    }
}

// Setup input/output buffers for zero-copy or regular mode
static bool setup_io_buffers() {
#if ZERO_COPY
    // Create input memory buffer for zero-copy mode
    input_mems[0] = rknn_create_mem(ctx, input_attrs[0].size_with_stride);
    if (!input_mems[0]) {
        LOGE("Failed to create input memory");
        return false;
    }
    
    memset(input_mems[0]->virt_addr, 0, input_attrs[0].size_with_stride);
    
    // Configure input tensor attributes
    input_attrs[0].index = 0;
    input_attrs[0].type = RKNN_TENSOR_UINT8;
    input_attrs[0].size = m_in_height * m_in_width * m_in_channel;
    input_attrs[0].fmt = RKNN_TENSOR_NHWC;
    input_attrs[0].pass_through = 0;
    
    if (rknn_set_io_mem(ctx, input_mems[0], &input_attrs[0]) < 0) {
        LOGE("Failed to set input memory");
        return false;
    }
    
    // Setup RGA destination buffer pointing to input memory
    g_rga_dst = wrapbuffer_virtualaddr(input_mems[0]->virt_addr, m_in_width, m_in_height, RK_FORMAT_RGB_888);

    // Create output memory buffers for each output tensor
    for (int i = 0; i < n_output; ++i) {
        output_mems[i] = rknn_create_mem(ctx, output_attrs[i].n_elems * sizeof(float));
        if (!output_mems[i]) {
            LOGE("Failed to create output memory %d", i);
            return false;
        }
        
        memset(output_mems[i]->virt_addr, 0, output_attrs[i].n_elems * sizeof(float));
        output_attrs[i].type = RKNN_TENSOR_FLOAT32;
        
        if (rknn_set_io_mem(ctx, output_mems[i], &output_attrs[i]) < 0) {
            LOGE("Failed to set output memory %d", i);
            return false;
        }
    }
#else
    // Allocate separate input buffer for non-zero-copy mode
    void *in_data = malloc(m_in_width * m_in_height * m_in_channel);
    if (!in_data) return false;
    g_rga_dst = wrapbuffer_virtualaddr(in_data, m_in_width, m_in_height, RK_FORMAT_RGB_888);
#endif
    return true;
}

// Initialize YOLO model: load model file, setup RKNN context, configure I/O
int create(int im_height, int im_width, int im_channel, char *model_path) {
    if (created) return 0;
    
    // Store input image dimensions
    img_height = im_height;
    img_width = im_width;

    // Load model file into memory
    FILE *fp = fopen(model_path, "rb");
    if (!fp) {
        LOGE("Failed to open model: %s", model_path);
        return -1;
    }
    
    fseek(fp, 0, SEEK_END);
    uint32_t model_len = ftell(fp);
    fseek(fp, 0, SEEK_SET);
    
    void *model = malloc(model_len);
    if (!model || model_len != fread(model, 1, model_len, fp)) {
        LOGE("Failed to read model");
        free(model);
        fclose(fp);
        return -1;
    }
    fclose(fp);

    // Initialize RKNN context with model data
    int ret = rknn_init(&ctx, model, model_len, 0, nullptr);
    free(model);
    if (ret < 0) {
        LOGE("rknn_init failed: %d", ret);
        return -1;
    }

    // Verify model has expected input/output count
    rknn_input_output_num io_num;
    if (rknn_query(ctx, RKNN_QUERY_IN_OUT_NUM, &io_num, sizeof(io_num)) != RKNN_SUCC ||
        io_num.n_input != n_input || io_num.n_output != n_output) {
        LOGE("Invalid model input/output");
        rknn_destroy(ctx);
        return -1;
    }

    // Query input tensor attributes to get model dimensions
    for (int i = 0; i < n_input; ++i) {
        input_attrs[i].index = i;
        if (rknn_query(ctx, RKNN_QUERY_INPUT_ATTR, &input_attrs[i], sizeof(rknn_tensor_attr)) < 0) {
            LOGE("Query input failed");
            rknn_destroy(ctx);
            return -1;
        }
    }

    // Extract model input dimensions based on tensor format
    if (input_attrs[0].fmt == RKNN_TENSOR_NHWC) {
        m_in_height = input_attrs[0].dims[1];
        m_in_width = input_attrs[0].dims[2];
        m_in_channel = input_attrs[0].dims[3];
    } else if (input_attrs[0].fmt == RKNN_TENSOR_NCHW) {
        m_in_height = input_attrs[0].dims[2];
        m_in_width = input_attrs[0].dims[3];
        m_in_channel = input_attrs[0].dims[1];
    } else {
        LOGE("Unsupported input format");
        rknn_destroy(ctx);
        return -1;
    }

    // Calculate scaling factors for post-processing
    scale_w = static_cast<float>(m_in_width) / img_width;
    scale_h = static_cast<float>(m_in_height) / img_height;

    // Query output tensor attributes and store quantization parameters
    out_scales.clear();
    out_zps.clear();
    for (int i = 0; i < n_output; ++i) {
        output_attrs[i].index = i;
        if (rknn_query(ctx, RKNN_QUERY_OUTPUT_ATTR, &output_attrs[i], sizeof(rknn_tensor_attr)) < 0) {
            LOGE("Query output failed");
            rknn_destroy(ctx);
            return -1;
        }
        out_scales.push_back(output_attrs[i].scale);
        out_zps.push_back(output_attrs[i].zp);
    }

    // Setup I/O buffers for inference
    if (!setup_io_buffers()) {
        cleanup_memory();
        rknn_destroy(ctx);
        return -1;
    }

    created = true;
    return 0;
}

// Cleanup and destroy RKNN context
void destroy() {
    if (!created) return;
    
    cleanup_memory();
    if (ctx) {
        rknn_destroy(ctx);
        ctx = 0;
    }
    created = false;
    out_scales.clear();
    out_zps.clear();
}

// Run YOLO inference: preprocess image, run model, copy outputs
bool run_model(char *inDataRaw, char *y0, char *y1, char *y2) {
    if (!created || !inDataRaw || !y0 || !y1 || !y2) {
        LOGE("Invalid parameters");
        return false;
    }

    // Setup source image buffer (RGBA format)
    g_rga_src = wrapbuffer_virtualaddr(inDataRaw, img_width, img_height, RK_FORMAT_RGBA_8888);
    
    // Resize and convert image: RGBA -> RGB at model input size
    if (imresize(g_rga_src, g_rga_dst) != IM_STATUS_SUCCESS) {
        LOGE("Image resize failed");
        return false;
    }

#if !ZERO_COPY
    // For non-zero-copy mode: set input data
    rknn_input inputs[1] = {{0, RKNN_TENSOR_UINT8, m_in_width * m_in_height * m_in_channel, 
                            RKNN_TENSOR_NHWC, 0, g_rga_dst.vir_addr}};
    if (rknn_inputs_set(ctx, 1, inputs) < 0) {
        LOGE("Set inputs failed");
        return false;
    }
#endif

    // Run inference
    if (rknn_run(ctx, nullptr) < 0) {
        LOGE("Inference failed");
        return false;
    }

    // Copy output tensors to provided buffers
#if ZERO_COPY
    // Direct memory copy from output buffers
    memcpy(y0, output_mems[0]->virt_addr, output_attrs[0].n_elems * sizeof(float));
    memcpy(y1, output_mems[1]->virt_addr, output_attrs[1].n_elems * sizeof(float));
    memcpy(y2, output_mems[2]->virt_addr, output_attrs[2].n_elems * sizeof(float));
#else
    // Get outputs and copy for non-zero-copy mode
    rknn_output outputs[3] = {{0, 1}, {0, 1}, {0, 1}};
    if (rknn_outputs_get(ctx, 3, outputs, nullptr) < 0) {
        LOGE("Get outputs failed");
        return false;
    }
    
    memcpy(y0, outputs[0].buf, output_attrs[0].n_elems * sizeof(float));
    memcpy(y1, outputs[1].buf, output_attrs[1].n_elems * sizeof(float));
    memcpy(y2, outputs[2].buf, output_attrs[2].n_elems * sizeof(float));
    
    rknn_outputs_release(ctx, 3, outputs);
#endif

    return true;
}

// Post-process YOLO outputs: NMS, coordinate scaling, result formatting
int post_process(float *grid0_buf, float *grid1_buf, float *grid2_buf,
                 int *ids, float *scores, float *boxes) {
    if (!created || !grid0_buf || !grid1_buf || !grid2_buf || !ids || !scores || !boxes) {
        LOGE("Invalid parameters");
        return -1;
    }

    // Run post-processing: decode predictions, apply NMS, scale coordinates
    detect_result_group_t detect_result_group;
    int ret = post_process(grid0_buf, grid1_buf, grid2_buf,
                          m_in_height, m_in_width, BOX_THRESH, NMS_THRESH, 
                          scale_w, scale_h, out_zps, out_scales, &detect_result_group);
    
    if (ret < 0) return -1;

    // Initialize output arrays
    memset(ids, 0, sizeof(int) * OBJ_NUMB_MAX_SIZE);
    memset(scores, 0, sizeof(float) * OBJ_NUMB_MAX_SIZE);
    memset(boxes, 0, sizeof(float) * OBJ_NUMB_MAX_SIZE * BOX_LEN);

    // Copy detection results to output arrays
    int count = std::min(detect_result_group.count, OBJ_NUMB_MAX_SIZE);
    for (int i = 0; i < count; ++i) {
        ids[i] = detect_result_group.results[i].class_id;
        scores[i] = detect_result_group.results[i].prop;
        // Copy bounding box coordinates [left, top, right, bottom]
        boxes[4*i + 0] = detect_result_group.results[i].box.left;
        boxes[4*i + 1] = detect_result_group.results[i].box.top;
        boxes[4*i + 2] = detect_result_group.results[i].box.right;
        boxes[4*i + 3] = detect_result_group.results[i].box.bottom;
    }

    return count;
}

// Color format conversion and image flipping using RGA
int colorConvertAndFlip(void *src, int srcFmt, void *dst, int dstFmt, 
                       int width, int height, int flip) {
    if (!src || !dst || width <= 0 || height <= 0) return -1;

    // Calculate buffer sizes for YUV420 to RGBA conversion
    int src_len = width * height * 3 / 2;
    int dst_len = width * height * 4;

    // Allocate 4K-aligned buffers for RGA operations
    void *src_aligned = malloc(src_len + 4096);
    void *dst_aligned = malloc(dst_len + 4096);
    
    if (!src_aligned || !dst_aligned) {
        free(src_aligned);
        free(dst_aligned);
        return -1;
    }

    void *org_src = src_aligned;
    void *org_dst = dst_aligned;

    // Align to 4K boundaries for optimal RGA performance
    src_aligned = (void*)((((int64_t)src_aligned >> 12) + 1) << 12);
    dst_aligned = (void*)((((int64_t)dst_aligned >> 12) + 1) << 12);

    // Copy source data to aligned buffer
    memcpy(src_aligned, src, src_len);

    // Create RGA buffer descriptors
    rga_buffer_t rga_src = wrapbuffer_virtualaddr(src_aligned, width, height, srcFmt);
    rga_buffer_t rga_dst = wrapbuffer_virtualaddr(dst_aligned, width, height, dstFmt);

    // Perform color conversion or flip operation
    int ret = (flip == DO_NOT_FLIP) ? 
        imcvtcolor(rga_src, rga_dst, rga_src.format, rga_dst.format) :
        imflip(rga_src, rga_dst, flip);

    // Copy result back to destination if successful
    if (ret == IM_STATUS_SUCCESS) {
        memcpy(dst, dst_aligned, dst_len);
    }

    // Cleanup allocated memory
    free(org_src);
    free(org_dst);
    return ret;
}