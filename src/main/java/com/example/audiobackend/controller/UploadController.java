package com.example.audiobackend.controller;

import com.example.audiobackend.common.Result;
import com.example.audiobackend.service.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin(originPatterns = "*")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/home")
    public Result<String> uploadHomeImage(@RequestParam("file") MultipartFile file) {
        try {
            String url = uploadService.uploadHomeImage(file);
            return Result.success(url);
        } catch (IllegalArgumentException e) {
            log.warn("上传主页图参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传主页图失败: {}", e.getMessage(), e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/product/cover")
    public Result<String> uploadProductCover(
            @RequestParam("categoryName") String categoryName,
            @RequestParam("productId") Long productId,
            @RequestParam("file") MultipartFile file) {
        try {
            String url = uploadService.uploadProductCover(categoryName, productId, file);
            return Result.success(url);
        } catch (IllegalArgumentException e) {
            log.warn("上传产品封面参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传产品封面失败: {}", e.getMessage(), e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/product/images")
    public Result<String[]> uploadProductImages(
            @RequestParam("categoryName") String categoryName,
            @RequestParam("productId") Long productId,
            @RequestParam("file") MultipartFile[] files) {
        try {
            String[] urls = uploadService.uploadProductImages(categoryName, productId, files);
            return Result.success(urls);
        } catch (IllegalArgumentException e) {
            log.warn("上传产品详情图参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传产品详情图失败: {}", e.getMessage(), e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }
}