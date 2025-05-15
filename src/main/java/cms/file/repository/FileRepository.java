package cms.file.repository;

import cms.file.entity.CmsFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<CmsFile, Long>, JpaSpecificationExecutor<CmsFile> {
    
    @Query("SELECT f FROM CmsFile f WHERE f.menu = :menu AND f.menuId = :menuId ORDER BY f.fileOrder ASC")
    List<CmsFile> findByMenuAndMenuIdOrderByFileOrderAsc(
            @Param("menu") String menu,
            @Param("menuId") Long menuId
    );
    
    @Query("SELECT f FROM CmsFile f WHERE f.menu = :menu AND f.menuId = :menuId AND f.publicYn = 'Y' ORDER BY f.fileOrder ASC")
    List<CmsFile> findPublicByMenuAndMenuId(@Param("menu") String menu, @Param("menuId") Long menuId);
    
    @Query("SELECT MAX(f.fileOrder) FROM CmsFile f WHERE f.menu = :menu AND f.menuId = :menuId")
    Integer findMaxFileOrder(@Param("menu") String menu, @Param("menuId") Long menuId);

    @Query("SELECT f FROM CmsFile f WHERE f.menu = :menu AND f.menuId = :menuId AND f.publicYn = :publicYn ORDER BY f.fileOrder ASC")
    List<CmsFile> findByMenuAndMenuIdAndPublicYnOrderByFileOrderAsc(
            @Param("menu") String menu,
            @Param("menuId") Long menuId,
            @Param("publicYn") String publicYn
    );

    @Query("SELECT f FROM CmsFile f WHERE f.menu = :menu AND f.menuId = :menuId AND f.publicYn = 'Y' ORDER BY f.fileOrder ASC")
    List<CmsFile> findPublicFilesByMenuAndMenuIdOrderByFileOrderAsc(
            @Param("menu") String menu,
            @Param("menuId") Long menuId
    );

    List<CmsFile> findByMenuAndMenuId(String menu, Long menuId);
    List<CmsFile> findByMenuAndMenuIdAndPublicYn(String menu, Long menuId, String publicYn);
    CmsFile findBySavedName(String savedName);

    List<CmsFile> findByMenuIn(List<String> menuTypes);
} 