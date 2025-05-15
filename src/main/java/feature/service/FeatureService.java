package feature.service;

import feature.domain.Feature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FeatureService {
    Feature createFeature(Feature feature);
    Feature updateFeature(Long id, Feature feature);
    void deleteFeature(Long id);
    Feature getFeature(Long id);
    Page<Feature> getFeatures(Pageable pageable);
    Page<Feature> searchFeatures(String keyword, Pageable pageable);
    Page<Feature> getFeaturesByType(String type, Pageable pageable);
    void activateFeature(Long id);
    void deactivateFeature(Long id);
} 