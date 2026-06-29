package com.caCommand.caCommand.services;

import java.io.File;

public interface StorageService {
    String uploadMedia(byte[] fileBytes, String fileName);
    String getSignedUrl(String urlStr);
    File downloadMediaLocally(String urlStr) throws Exception;
}
