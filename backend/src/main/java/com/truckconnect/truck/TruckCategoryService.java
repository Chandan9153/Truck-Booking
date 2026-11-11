package com.truckconnect.truck;

import com.truckconnect.truck.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class TruckCategoryService {

    private final TruckCategoryRepository repository;

    public TruckCategoryService(TruckCategoryRepository repository) {
        this.repository = repository;
    }

    public List<TruckCategoryResponse> listActive() {
        return repository.findByActiveTrueOrderByTyreCountAsc()
                .stream().map(TruckCategoryResponse::from).toList();
    }

    public List<TruckCategoryResponse> listAll() {
        return repository.findAll().stream()
                .map(TruckCategoryResponse::from).toList();
    }

    @Transactional
    public TruckCategoryResponse create(TruckCategoryRequest req) {
        if (repository.existsByName(req.name())) {
            throw new IllegalArgumentException("Category name already exists: " + req.name());
        }
        TruckCategory saved = repository.save(new TruckCategory(
                req.name(), req.tyreCount(), req.targetPayloadTons(), req.description()));
        return TruckCategoryResponse.from(saved);
    }

    @Transactional
    public TruckCategoryResponse update(Long id, TruckCategoryRequest req) {
        TruckCategory category = getOrThrow(id);
        if (!category.getName().equals(req.name()) && repository.existsByName(req.name())) {
            throw new IllegalArgumentException("Category name already exists: " + req.name());
        }
        category.setName(req.name());
        category.setTyreCount(req.tyreCount());
        category.setTargetPayloadTons(req.targetPayloadTons());
        category.setDescription(req.description());
        return TruckCategoryResponse.from(repository.save(category));
    }

    @Transactional
    public void deactivate(Long id) {
        TruckCategory category = getOrThrow(id);
        category.setActive(false);
        repository.save(category);
    }

    // Used by TruckService: a new truck may only use an existing, active category.
    public TruckCategory getActiveOrThrow(Long id) {
        TruckCategory category = getOrThrow(id);
        if (!category.isActive()) {
            throw new IllegalArgumentException("Category is not active: " + id);
        }
        return category;
    }

    private TruckCategory getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Category not found: " + id));
    }
}
