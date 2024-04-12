package com.bingo.hotel.spa.intl.core.api.aichotels.bean.hotel.room;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;

import java.util.List;

public class RoomInfoResponse implements BaseResponse {
    private ResultBean result;
    private List<RoomListBean> room_list;

    public ResultBean getResult() {
        return result;
    }

    public void setResult(ResultBean result) {
        this.result = result;
    }

    public List<RoomListBean> getRoom_list() {
        return room_list;
    }

    public void setRoom_list(List<RoomListBean> room_list) {
        this.room_list = room_list;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public static class ResultBean {
        /**
         * return_status : {"success":"true","exception":""}
         */

        private ReturnStatusBean return_status;

        public ReturnStatusBean getReturn_status() {
            return return_status;
        }

        public void setReturn_status(ReturnStatusBean return_status) {
            this.return_status = return_status;
        }

        public static class ReturnStatusBean {
            /**
             * success : true
             * exception :
             */

            private String success;
            private String exception;

            public String getSuccess() {
                return success;
            }

            public void setSuccess(String success) {
                this.success = success;
            }

            public String getException() {
                return exception;
            }

            public void setException(String exception) {
                this.exception = exception;
            }
        }
    }

    public static class RoomListBean {
        /**
         * room_type : 1013
         * room_name : Andaz King
         * room_name_zh : 安达仕房（特大床）
         * room_desc : null
         * room_size : null
         * bed_info : null
         * room_pics : []
         * nonsmoking : null
         * amenities : null
         * max_occupancy : null
         */

        private String room_type;
        private String room_name;
        private String room_name_zh;
        private String room_desc;
        private String room_size;
        private Object bed_info;
        private Integer nonsmoking;
        private Object amenities;
        private Object max_occupancy;
        private Integer window;
        private List<String> room_pics;

        public void setRoom_type(String room_type) {
            this.room_type = room_type;
        }

        public void setRoom_name(String room_name) {
            this.room_name = room_name;
        }

        public void setRoom_name_zh(String room_name_zh) {
            this.room_name_zh = room_name_zh;
        }

        public void setRoom_desc(String room_desc) {
            this.room_desc = room_desc;
        }

        public void setRoom_size(String room_size) {
            this.room_size = room_size;
        }


        public void setNonsmoking(Integer nonsmoking) {
            this.nonsmoking = nonsmoking;
        }

        public void setAmenities(Object amenities) {
            this.amenities = amenities;
        }

        public void setMax_occupancy(Object max_occupancy) {
            this.max_occupancy = max_occupancy;
        }

        public void setWindow(Integer window) {
            this.window = window;
        }

        public void setRoom_pics(List<String> room_pics) {
            this.room_pics = room_pics;
        }

        public String getRoom_type() {
            return room_type;
        }

        public String getRoom_name() {
            return room_name;
        }

        public String getRoom_name_zh() {
            return room_name_zh;
        }

        public String getRoom_desc() {
            return room_desc;
        }

        public String getRoom_size() {
            return room_size;
        }

        public Object getBed_info() {
            return bed_info;
        }

        public void setBed_info(Object bed_info) {
            this.bed_info = bed_info;
        }

        public Integer getNonsmoking() {
            return nonsmoking;
        }

        public Object getAmenities() {
            return amenities;
        }

        public Object getMax_occupancy() {
            return max_occupancy;
        }

        public Integer getWindow() {
            return window;
        }

        public List<String> getRoom_pics() {
            return room_pics;
        }
    }
}
