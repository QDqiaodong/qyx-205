package com.coldchain.service.impl;

import com.coldchain.dto.request.CodeBindRequest;
import com.coldchain.dto.request.CodeReassignRequest;
import com.coldchain.dto.request.ShelfCreateRequest;
import com.coldchain.dto.response.CodeMappingResponse;
import com.coldchain.dto.response.ShelfResponse;
import com.coldchain.dto.response.ZoneTreeResponse;
import com.coldchain.entity.CodeChangeLog;
import com.coldchain.entity.LocationCode;
import com.coldchain.entity.Shelf;
import com.coldchain.exception.BusinessException;
import com.coldchain.repository.CodeChangeLogRepository;
import com.coldchain.repository.LocationCodeRepository;
import com.coldchain.repository.ShelfRepository;
import com.coldchain.service.ShelfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShelfServiceImpl implements ShelfService {

    private final ShelfRepository shelfRepository;
    private final LocationCodeRepository locationCodeRepository;
    private final CodeChangeLogRepository codeChangeLogRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String REDIS_KEY_PREFIX = "shelf:capacity:";
    private static final int REDIS_TTL_HOURS = 24;
    private static final int OPERATION_BIND = 1;
    private static final int OPERATION_UNBIND = 2;
    private static final int OPERATION_REASSIGN = 3;

    @Override
    @Transactional
    public ShelfResponse createShelf(ShelfCreateRequest request) {
        if (shelfRepository.existsByShelfNo(request.getShelfNo())) {
            throw new BusinessException("货架编号已存在");
        }

        Shelf shelf = Shelf.builder()
                .shelfNo(request.getShelfNo())
                .capacity(request.getCapacity())
                .zone(request.getZone())
                .status(1)
                .build();

        shelf = shelfRepository.save(shelf);

        redisTemplate.opsForValue().set(
                REDIS_KEY_PREFIX + shelf.getId(),
                shelf.getCapacity(),
                REDIS_TTL_HOURS,
                TimeUnit.HOURS
        );

        return buildShelfResponse(shelf, null);
    }

    @Override
    public ShelfResponse getShelfById(Long id) {
        Shelf shelf = shelfRepository.findById(id)
                .orElseThrow(() -> new BusinessException("货架不存在"));
        
        LocationCode locationCode = locationCodeRepository.findByShelfId(id).orElse(null);
        return buildShelfResponse(shelf, locationCode);
    }

    @Override
    public ShelfResponse getShelfByNo(String shelfNo) {
        Shelf shelf = shelfRepository.findByShelfNo(shelfNo)
                .orElseThrow(() -> new BusinessException("货架不存在"));
        
        LocationCode locationCode = locationCodeRepository.findByShelfId(shelf.getId()).orElse(null);
        return buildShelfResponse(shelf, locationCode);
    }

    @Override
    public List<ShelfResponse> getAllShelves() {
        List<Shelf> shelves = shelfRepository.findAllByOrderByZoneAscShelfNoAsc();
        Map<Long, LocationCode> codeMap = locationCodeRepository.findAll().stream()
                .filter(c -> c.getShelfId() != null)
                .collect(Collectors.toMap(LocationCode::getShelfId, c -> c));
        
        return shelves.stream()
                .map(s -> buildShelfResponse(s, codeMap.get(s.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public List<ShelfResponse> getShelvesByZone(String zone) {
        List<Shelf> shelves = shelfRepository.findByZone(zone);
        Map<Long, LocationCode> codeMap = locationCodeRepository.findAll().stream()
                .filter(c -> c.getShelfId() != null)
                .collect(Collectors.toMap(LocationCode::getShelfId, c -> c));
        
        return shelves.stream()
                .map(s -> buildShelfResponse(s, codeMap.get(s.getId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteShelf(Long id) {
        Shelf shelf = shelfRepository.findById(id)
                .orElseThrow(() -> new BusinessException("货架不存在"));

        LocationCode locationCode = locationCodeRepository.findByShelfId(id).orElse(null);
        if (locationCode != null) {
            locationCode.setShelfId(null);
            locationCode.setStatus(1);
            locationCodeRepository.save(locationCode);

            saveChangeLog(shelf.getId(), shelf.getShelfNo(), locationCode.getCode(), null, OPERATION_UNBIND, "system", "删除货架自动解绑");
        }

        shelfRepository.delete(shelf);
        redisTemplate.delete(REDIS_KEY_PREFIX + id);
    }

    @Override
    @Transactional
    public ShelfResponse bindCode(CodeBindRequest request) {
        Shelf shelf = shelfRepository.findById(request.getShelfId())
                .orElseThrow(() -> new BusinessException("货架不存在"));

        LocationCode existingCode = locationCodeRepository.findByShelfId(request.getShelfId()).orElse(null);
        if (existingCode != null) {
            throw new BusinessException("货架已绑定货位编码: " + existingCode.getCode());
        }

        LocationCode locationCode = locationCodeRepository.findByCode(request.getCode())
                .orElseGet(() -> {
                    LocationCode newCode = LocationCode.builder()
                            .code(request.getCode())
                            .status(1)
                            .build();
                    return locationCodeRepository.save(newCode);
                });

        if (locationCode.getShelfId() != null) {
            throw new BusinessException("货位编码已被其他货架绑定");
        }

        locationCode.setShelfId(request.getShelfId());
        locationCode.setStatus(2);
        locationCodeRepository.save(locationCode);

        saveChangeLog(shelf.getId(), shelf.getShelfNo(), null, request.getCode(), OPERATION_BIND,
                request.getOperator(), request.getRemark());

        return buildShelfResponse(shelf, locationCode);
    }

    @Override
    @Transactional
    public ShelfResponse unbindCode(Long shelfId, String operator, String remark) {
        Shelf shelf = shelfRepository.findById(shelfId)
                .orElseThrow(() -> new BusinessException("货架不存在"));

        LocationCode locationCode = locationCodeRepository.findByShelfId(shelfId)
                .orElseThrow(() -> new BusinessException("货架未绑定货位编码"));

        String oldCode = locationCode.getCode();

        locationCode.setShelfId(null);
        locationCode.setStatus(1);
        locationCodeRepository.save(locationCode);

        saveChangeLog(shelf.getId(), shelf.getShelfNo(), oldCode, null, OPERATION_UNBIND,
                operator, remark);

        return buildShelfResponse(shelf, null);
    }

    @Override
    @Transactional
    public ShelfResponse reassignCode(CodeReassignRequest request) {
        Shelf shelf = shelfRepository.findById(request.getShelfId())
                .orElseThrow(() -> new BusinessException("货架不存在"));

        LocationCode oldCode = locationCodeRepository.findByShelfId(request.getShelfId()).orElse(null);

        LocationCode newCode = locationCodeRepository.findByCode(request.getNewCode())
                .orElseGet(() -> {
                    LocationCode code = LocationCode.builder()
                            .code(request.getNewCode())
                            .status(1)
                            .build();
                    return locationCodeRepository.save(code);
                });

        if (newCode.getShelfId() != null && !newCode.getShelfId().equals(request.getShelfId())) {
            throw new BusinessException("新货位编码已被其他货架绑定");
        }

        if (oldCode != null) {
            oldCode.setShelfId(null);
            oldCode.setStatus(1);
            locationCodeRepository.save(oldCode);
        }

        newCode.setShelfId(request.getShelfId());
        newCode.setStatus(2);
        locationCodeRepository.save(newCode);

        saveChangeLog(shelf.getId(), shelf.getShelfNo(),
                oldCode != null ? oldCode.getCode() : null,
                request.getNewCode(), OPERATION_REASSIGN,
                request.getOperator(), request.getRemark());

        return buildShelfResponse(shelf, newCode);
    }

    @Override
    public ShelfResponse searchByCode(String code) {
        LocationCode locationCode = locationCodeRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException("货位编码不存在"));

        if (locationCode.getShelfId() == null) {
            throw new BusinessException("货位编码未绑定货架");
        }

        Shelf shelf = shelfRepository.findById(locationCode.getShelfId())
                .orElseThrow(() -> new BusinessException("关联货架不存在"));

        return buildShelfResponse(shelf, locationCode);
    }

    @Override
    public List<ZoneTreeResponse> getZoneTree() {
        List<Shelf> shelves = shelfRepository.findAllByOrderByZoneAscShelfNoAsc();
        Map<Long, LocationCode> codeMap = locationCodeRepository.findAll().stream()
                .filter(c -> c.getShelfId() != null)
                .collect(Collectors.toMap(LocationCode::getShelfId, c -> c));

        Map<String, List<ZoneTreeResponse.ShelfItem>> zoneMap = new LinkedHashMap<>();

        for (Shelf shelf : shelves) {
            LocationCode code = codeMap.get(shelf.getId());
            String codeStr = code != null ? code.getCode() : "未绑定";

            ZoneTreeResponse.ShelfItem item = new ZoneTreeResponse.ShelfItem();
            item.setId(shelf.getId());
            item.setShelfNo(shelf.getShelfNo());
            item.setLocationCode(codeStr);
            item.setLabel(shelf.getShelfNo() + " (" + codeStr + ")");

            zoneMap.computeIfAbsent(shelf.getZone(), k -> new ArrayList<>()).add(item);
        }

        return zoneMap.entrySet().stream()
                .map(entry -> {
                    ZoneTreeResponse zone = new ZoneTreeResponse();
                    zone.setLabel(entry.getKey());
                    zone.setValue(entry.getKey());
                    zone.setChildren(entry.getValue());
                    return zone;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CodeMappingResponse> getAllCodeMappings() {
        List<Shelf> shelves = shelfRepository.findAllByOrderByZoneAscShelfNoAsc();
        Map<Long, LocationCode> codeMap = locationCodeRepository.findAll().stream()
                .filter(c -> c.getShelfId() != null)
                .collect(Collectors.toMap(LocationCode::getShelfId, c -> c));

        return shelves.stream()
                .map(shelf -> {
                    LocationCode code = codeMap.get(shelf.getId());
                    CodeMappingResponse response = new CodeMappingResponse();
                    response.setShelfId(shelf.getId());
                    response.setShelfNo(shelf.getShelfNo());
                    response.setCapacity(shelf.getCapacity());
                    response.setZone(shelf.getZone());
                    response.setLocationCode(code != null ? code.getCode() : "");
                    response.setBindTime(code != null ? code.getUpdatedAt() : null);
                    return response;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<CodeChangeLog> getChangeLogsByShelfId(Long shelfId) {
        return codeChangeLogRepository.findByShelfIdOrderByCreatedAtDesc(shelfId);
    }

    @Override
    public List<CodeChangeLog> getAllChangeLogs() {
        return codeChangeLogRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public String exportCodeMapping() {
        List<CodeMappingResponse> mappings = getAllCodeMappings();
        
        StringWriter writer = new StringWriter();
        writer.write("货架ID,货架编号,承重(kg),所属库区,货位编码,绑定时间\n");
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        for (CodeMappingResponse mapping : mappings) {
            writer.write(mapping.getShelfId() + ",");
            writer.write(mapping.getShelfNo() + ",");
            writer.write(mapping.getCapacity() + ",");
            writer.write(mapping.getZone() + ",");
            writer.write(mapping.getLocationCode() != null ? mapping.getLocationCode() : "");
            writer.write(",");
            writer.write(mapping.getBindTime() != null ? mapping.getBindTime().format(formatter) : "");
            writer.write("\n");
        }
        
        return writer.toString();
    }

    private ShelfResponse buildShelfResponse(Shelf shelf, LocationCode locationCode) {
        ShelfResponse response = new ShelfResponse();
        response.setId(shelf.getId());
        response.setShelfNo(shelf.getShelfNo());
        response.setCapacity(shelf.getCapacity());
        response.setZone(shelf.getZone());
        response.setStatus(shelf.getStatus());
        response.setLocationCode(locationCode != null ? locationCode.getCode() : null);
        response.setCreatedAt(shelf.getCreatedAt());
        response.setUpdatedAt(shelf.getUpdatedAt());
        return response;
    }

    private void saveChangeLog(Long shelfId, String shelfNo, String oldCode, String newCode,
                               Integer operationType, String operator, String remark) {
        CodeChangeLog log = CodeChangeLog.builder()
                .shelfId(shelfId)
                .shelfNo(shelfNo)
                .oldCode(oldCode)
                .newCode(newCode)
                .operationType(operationType)
                .operator(operator != null ? operator : "system")
                .remark(remark)
                .build();
        codeChangeLogRepository.save(log);
    }
}
