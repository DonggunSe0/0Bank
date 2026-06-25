package com.team10.backend.domain.youngPolicy.type;

public enum Region {
    SEOUL("11", "서울", "서울특별시"),
    BUSAN("26", "부산", "부산광역시"),
    DAEGU("27", "대구", "대구광역시"),
    INCHEON("28", "인천", "인천광역시"),
    GWANGJU("29", "광주", "광주광역시"),
    DAEJEON("30", "대전", "대전광역시"),
    ULSAN("31", "울산", "울산광역시"),
    SEJONG("36", "세종", "세종특별자치시"),
    GYEONGGI("41", "경기", "경기도"),
    GANGWON("51", "강원", "강원특별자치도", "강원도"),
    GANGWON_LEGACY("42", "강원", "강원도"),
    CHUNGBUK("43", "충북", "충청북도"),
    CHUNGNAM("44", "충남", "충청남도"),
    JEONBUK("52", "전북", "전북특별자치도", "전라북도"),
    JEONBUK_LEGACY("45", "전북", "전라북도"),
    JEONNAM("46", "전남", "전라남도"),
    GYEONGBUK("47", "경북", "경상북도"),
    GYEONGNAM("48", "경남", "경상남도"),
    JEJU("49", "제주", "제주특별자치도", "제주도");

    private final String code;
    private final String[] names;

    Region(String code, String... names) {
        this.code = code;
        this.names = names;
    }

    public String getCode() {
        return code;
    }

    /**
     * 한글 지역명을 기반으로 알맞은 시도 코드를 조회합니다.
     * 예: "서울 마포구" -> "11", "경기도 성남시" -> "41"
     */
    public static String findCodeByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        // 전국에 대한 특수 처리
        if (name.contains("전국")) {
            return "003002001";
        }

        for (Region region : values()) {
            for (String n : region.names) {
                if (name.contains(n)) {
                    return region.code;
                }
            }
        }
        return null;
    }
}
