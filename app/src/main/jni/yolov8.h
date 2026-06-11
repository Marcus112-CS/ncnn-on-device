// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2024 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#ifndef YOLOV8_H
#define YOLOV8_H

#include <opencv2/core/core.hpp>

#include <net.h>

#include <set>

struct KeyPoint
{
    cv::Point2f p;
    float prob;
};

struct Object
{
    cv::Rect_<float> rect;
    cv::RotatedRect rrect;
    int label;
    float prob;
    int gindex;
    cv::Mat mask;
    std::vector<KeyPoint> keypoints;
};

class YOLOv8
{
public:
    virtual ~YOLOv8();

    int load(const char* parampath, const char* modelpath, bool use_gpu = false);
    int load(AAssetManager* mgr, const char* parampath, const char* modelpath, bool use_gpu = false);

    void set_det_target_size(int target_size);

    virtual int detect(const cv::Mat& rgb, std::vector<Object>& objects) = 0;
    virtual int draw(cv::Mat& rgb, const std::vector<Object>& objects) = 0;

protected:
    ncnn::Net yolov8;
    int det_target_size;
};

class YOLOv8_det : public YOLOv8
{
public:
    virtual int detect(const cv::Mat& rgb, std::vector<Object>& objects);

protected:
    // class indices to keep; empty = keep all. Applied during detection so a box
    // is reported whenever a whitelisted class scores above threshold, even if a
    // non-whitelisted class scored higher for that cell.
    std::set<int> class_whitelist;

    // confidence threshold; subclasses may lower it (e.g. OIV7's 601 sparse classes).
    float prob_threshold = 0.25f;
};

// COCO detection base: draws using the 80-class COCO name table. Subclasses below
// share this draw and only differ by which class indices they keep (class_whitelist).
class YOLOv8_det_coco : public YOLOv8_det
{
public:
    virtual int draw(cv::Mat& rgb, const std::vector<Object>& objects);
};

class YOLOv8_det_traffic : public YOLOv8_det_coco
{
public:
    YOLOv8_det_traffic();
};

class YOLOv8_det_airport : public YOLOv8_det_coco
{
public:
    YOLOv8_det_airport();
};

// 安监: dedicated 2-class (helmet, head) detector; raw output, shares YOLOv8_det::detect.
class YOLOv8_det_helmet : public YOLOv8_det
{
public:
    virtual int draw(cv::Mat& rgb, const std::vector<Object>& objects);
};

class YOLOv8_pose : public YOLOv8
{
public:
    virtual int detect(const cv::Mat& rgb, std::vector<Object>& objects);
    virtual int draw(cv::Mat& rgb, const std::vector<Object>& objects);
};

#endif // YOLOV8_H
