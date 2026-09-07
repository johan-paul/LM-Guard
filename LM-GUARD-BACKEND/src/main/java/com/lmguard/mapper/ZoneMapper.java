package com.lmguard.mapper;

import com.lmguard.dto.zone.ZoneResponse;
import com.lmguard.entity.Zone;
import org.springframework.stereotype.Component;

@Component
public class ZoneMapper {

    public ZoneResponse toResponse(Zone zone) {
        if (zone == null) {
            return null;
        }
        return new ZoneResponse(zone.getId(), zone.getName(), zone.getCode(), zone.getOffice(), zone.getDistrict());
    }
}
