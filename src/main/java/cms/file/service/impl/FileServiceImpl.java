package cms.file.service.impl;

import cms.file.entity.CmsFile;
import cms.file.repository.FileRepository;
import cms.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.io.FilenameUtils;
import cms.board.repository.BbsArticleRepository;
import java.util.ArrayList;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {

    private final FileRepository fileRepository;
    private final BbsArticleRepository bbsArticleRepository;

    @Value("${spring.file.storage.local.base-path}")
    private String basePath;

    @Override
    @Transactional
    public List<CmsFile> uploadFiles(String menu, Long menuId, List<MultipartFile> files) {
        List<CmsFile> uploadedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String originalFilename = file.getOriginalFilename();
                String ext = FilenameUtils.getExtension(originalFilename);
                String uuidFileName = generateUUIDFileName(ext);

                String dateSubDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                // DB에 저장될 상대 경로: "<uploadPath>/<date>/<uuid.ext>"
                String relativeSavePath = Paths.get(dateSubDir, uuidFileName).toString().replace("\\", "/");

                try {
                    // 물리적 파일 저장 경로: "<basePath>/<uploadPath>/<date>/<uuid.ext>"
                    Path targetDirectory = Paths.get(basePath, dateSubDir);
                    Files.createDirectories(targetDirectory);
                    Path targetLocation = targetDirectory.resolve(uuidFileName);

                    Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

                    CmsFile fileEntity = new CmsFile();
                    fileEntity.setMenu(menu); // "BBS", "CONTENT" 등
                    fileEntity.setMenuId(menuId);
                    fileEntity.setOriginName(originalFilename);
                    fileEntity.setSavedName(relativeSavePath); // 타입, 날짜 포함 상대 경로 저장
                    fileEntity.setMimeType(file.getContentType());
                    fileEntity.setSize(file.getSize());
                    fileEntity.setExt(ext);
                    fileEntity.setPublicYn("Y");

                    Integer maxOrder = fileRepository.findMaxFileOrder(menu, menuId);
                    fileEntity.setFileOrder(maxOrder != null ? maxOrder + 1 : 0);

                    uploadedFiles.add(fileRepository.save(fileEntity));
                } catch (IOException ex) {
                    throw new RuntimeException(
                            "Could not store file " + originalFilename + ". Error: " + ex.getMessage(), ex);
                }
            }
        }
        return uploadedFiles;
    }

    private String generateUUIDFileName(String extension) {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        if (extension != null && !extension.isEmpty()) {
            return String.format("%s.%s", uuid, extension);
        }
        return uuid;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CmsFile> getList(String menu, Long menuId, String publicYn) {
        validatePublicYn(publicYn);
        if (publicYn != null) {
            return fileRepository.findByMenuAndMenuIdAndPublicYnOrderByFileOrderAsc(menu, menuId, publicYn);
        }
        return fileRepository.findByMenuAndMenuIdOrderByFileOrderAsc(menu, menuId);
    }

    @Override
    @Transactional(readOnly = true)
    public CmsFile getFile(Long fileId) {
        return fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다. ID: " + fileId));
    }

    @Override
    @Transactional
    public CmsFile updateFile(Long fileId, CmsFile fileDetails) {
        CmsFile existingFile = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다. ID: " + fileId));

        validatePublicYn(fileDetails.getPublicYn());

        existingFile.setPublicYn(fileDetails.getPublicYn());
        existingFile.setFileOrder(fileDetails.getFileOrder());

        return fileRepository.save(existingFile);
    }

    @Override
    @Transactional
    public void deleteFile(Long fileId) {
        CmsFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다. ID: " + fileId));

        try {
            Path filePath = Paths.get(basePath, file.getSavedName());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            System.err.println("Error deleting physical file: " + e.getMessage());
        }

        fileRepository.delete(file);
    }

    @Override
    @Transactional
    public void updateFileOrder(List<CmsFile> files) {
        for (CmsFile file : files) {
            CmsFile existingFile = fileRepository.findById(file.getFileId())
                    .orElseThrow(() -> new RuntimeException("File not found with ID: " + file.getFileId()));
            existingFile.setFileOrder(file.getFileOrder());
            fileRepository.save(existingFile);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CmsFile> getPublicList(String menu, Long menuId) {
        return fileRepository.findPublicFilesByMenuAndMenuIdOrderByFileOrderAsc(menu, menuId);
    }

    @Override
    public Resource loadFileAsResource(String savedName) {
        try {
            Path filePath = Paths.get(basePath).resolve(savedName).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + savedName);
            }
        } catch (MalformedURLException ex) {
            throw new RuntimeException("File path is invalid: " + savedName, ex);
        }
    }

    @Override
    public List<CmsFile> getAllFiles(String menu, String publicYn, int page, int size) {
        Specification<CmsFile> spec = Specification.where(null);

        if (menu != null && !menu.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("menu"), menu));
        }

        if (publicYn != null && !publicYn.isEmpty()) {
            validatePublicYn(publicYn);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("publicYn"), publicYn));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdDate").descending());
        return fileRepository.findAll(spec, pageable).getContent();
    }

    private void validatePublicYn(String publicYn) {
        if (publicYn != null && !publicYn.matches("[YN]")) {
            throw new IllegalArgumentException("publicYn must be either 'Y' or 'N'");
        }
    }

    @Override
    @Transactional
    public int deleteOrphanedFilesByMissingArticle(List<String> menuTypes) {
        log.info("Starting deletion of orphaned files for menu types: {}", menuTypes);
        List<CmsFile> candidateFiles = fileRepository.findByMenuIn(menuTypes);

        int deletedCount = 0;
        List<CmsFile> filesToDelete = new ArrayList<>();

        for (CmsFile file : candidateFiles) {
            if (file.getMenuId() == null) {
                continue;
            }
            if (!bbsArticleRepository.existsById(file.getMenuId())) {
                filesToDelete.add(file);
            }
        }

        if (filesToDelete.isEmpty()) {
            log.info("No orphaned files found to delete for menu types: {}", menuTypes);
            return 0;
        }

        log.info("Found {} orphaned files to delete.", filesToDelete.size());

        for (CmsFile file : filesToDelete) {
            try {
                Path filePath = Paths.get(basePath, file.getSavedName());
                Files.deleteIfExists(filePath);
                fileRepository.delete(file);
                deletedCount++;
                log.info("Orphaned file deleted (Article ID: {} not found): File ID={}, Stored Name={}",
                        file.getMenuId(), file.getFileId(), file.getSavedName());
            } catch (IOException e) {
                log.error("Error deleting physical orphaned file: {}. File ID: {}, Stored Name: {}", e.getMessage(),
                        file.getFileId(), file.getSavedName(), e);
            } catch (Exception e) {
                log.error("Error deleting orphaned file record from DB: {}. File ID: {}, Stored Name: {}",
                        e.getMessage(), file.getFileId(), file.getSavedName(), e);
            }
        }
        log.info("Finished deletion of orphaned files. Total deleted: {}", deletedCount);
        return deletedCount;
    }
}