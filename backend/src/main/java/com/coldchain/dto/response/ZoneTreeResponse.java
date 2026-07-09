package com.coldchain.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class ZoneTreeResponse {

    private String label;
    private String value;
    private List<ShelfItem> children;

    @Data
    public static class ShelfItem {
        private Long id;
        private String shelfNo;
        private String locationCode;
        private String label;
    }
}
