package com.example.audiobackend.service;

import org.springframework.web.multipart.MultipartFile;

public interface UploadService {

    String uploadHomeImage(MultipartFile file);

    String uploadProductCover(String categoryName, Long productId, MultipartFile file);

    String[] uploadProductImages(String categoryName, Long productId, MultipartFile[] files);
}