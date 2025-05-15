package feature.service.impl;

import feature.domain.Feature;
import feature.repository.FeatureRepository;
import feature.service.FeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FeatureServiceImpl implements FeatureService {
    private final FeatureRepository featureRepository;

    @Override
    public Feature createFeature(Feature feature) {
        return featureRepository.save(feature);
    }

    @Override
    public Feature updateFeature(Long id, Feature feature) {
        Feature existingFeature = featureRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Feature not found"));
        
        existingFeature.update(
            feature.getName(),
            feature.getType(),
            feature.getDescription()
        );
        
        return featureRepository.save(existingFeature);
    }

    @Override
    public void deleteFeature(Long id) {
        featureRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Feature getFeature(Long id) {
        return featureRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Feature not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Feature> getFeatures(Pageable pageable) {
        return featureRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Feature> searchFeatures(String keyword, Pageable pageable) {
        return featureRepository.searchFeatures(keyword, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Feature> getFeaturesByType(String type, Pageable pageable) {
        return featureRepository.findByType(type, pageable);
    }

    @Override
    public void activateFeature(Long id) {
        Feature feature = featureRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Feature not found"));
        feature.activate();
        featureRepository.save(feature);
    }

    @Override
    public void deactivateFeature(Long id) {
        Feature feature = featureRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Feature not found"));
        feature.deactivate();
        featureRepository.save(feature);
    }
} 