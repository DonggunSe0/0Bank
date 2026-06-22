package com.team10.backend.domain.investment.config;

import java.time.ZoneId;

public final class KisConstants {

    private KisConstants() {
    }

    public static final String BASE_URL = "https://openapi.koreainvestment.com:9443";
    public static final String WEB_SOCKET_URL = "ws://ops.koreainvestment.com:21000";

    public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    public static final class Header {

        public static final String APP_KEY = "appkey";
        public static final String APP_SECRET = "appsecret";
        public static final String SECRET_KEY = "secretkey";
        public static final String TR_ID = "tr_id";
        public static final String AUTHORIZATION = "authorization";
        public static final String CONTENT_TYPE = "content-type";
        public static final String CUST_TYPE = "custtype";

        private Header() {
        }

    }

    public static final class Auth {

        public static final String TOKEN_ISSUE_PATH = "/oauth2/tokenP";
        public static final String TOKEN_REVOKE_PATH = "/oauth2/revokeP";
        public static final String WEBSOCKET_APPROVAL_PATH = "/oauth2/Approval";

        private Auth() {
        }

    }

    public static final class MarketHoliday {

        public static final String MARKET_HOLIDAY_PATH = "/uapi/domestic-stock/v1/quotations/chk-holiday";
        public static final String MARKET_HOLIDAY_TR_ID = "CTCA0903R";

        private MarketHoliday() {

        }

    }

    public static final class StockMaster {

        public static final String KOSPI_MASTER_DOWNLOAD_URL =
                "https://new.real.download.dws.co.kr/common/master/kospi_code.mst.zip";
        public static final String KOSPI_MASTER_FILE_NAME = "kospi_code.mst";

        private StockMaster() {

        }

    }

    public static final class RealtimeWebSocket {

        public static final String APPROVAL_KEY = "approval_key";
        public static final String TR_TYPE = "tr_type";
        public static final String BODY = "body";
        public static final String HEADER = "header";
        public static final String INPUT = "input";
        public static final String TR_KEY = "tr_key";

        public static final String ORDERBOOK_TR_ID = "H0STASP0";
        public static final String PINGPONG_TR_ID = "PINGPONG";
        public static final String PERSONAL_CUST_TYPE = "P";
        public static final String UTF_8_CONTENT_TYPE = "utf-8";
        public static final String SUBSCRIBE_TR_TYPE = "1";
        public static final String UNSUBSCRIBE_TR_TYPE = "2";

        private RealtimeWebSocket() {

        }

    }

}
