package com.trip.booking.spa.core.api.aichotels.bean.hotel.city;

import com.trip.booking.spa.core.api.common.asynchttp.BaseResponse;

import java.util.List;

public class CityListResponse implements BaseResponse {

    /**
     * result : {"return_status":{"success":"true","exception":""}}
     * city_list : [{"city_id":"264","name":"Allentown","areaname":"Pennsylvania","center_latitude":"40.602180","center_longitude":"-75.471695"},{"city_id":"aic271","name":"Albuquerque","areaname":"New Mexico","center_latitude":"35.083679","center_longitude":"-106.644653"}]
     */

    private ResultBean result;
    private List<CityListBean> city_list;

    public ResultBean getResult() {
        return result;
    }

    public void setResult(ResultBean result) {
        this.result = result;
    }

    public List<CityListBean> getCity_list() {
        return city_list;
    }

    public void setCity_list(List<CityListBean> city_list) {
        this.city_list = city_list;
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

    public static class CityListBean {
        /**
         * city_id : 264
         * name : Allentown
         * areaname : Pennsylvania
         * center_latitude : 40.602180
         * center_longitude : -75.471695
         */

        private String city_id;
        private String name;
        private String areaname;
        private String center_latitude;
        private String center_longitude;

        public String getCity_id() {
            return city_id;
        }

        public void setCity_id(String city_id) {
            this.city_id = city_id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getAreaname() {
            return areaname;
        }

        public void setAreaname(String areaname) {
            this.areaname = areaname;
        }

        public String getCenter_latitude() {
            return center_latitude;
        }

        public void setCenter_latitude(String center_latitude) {
            this.center_latitude = center_latitude;
        }

        public String getCenter_longitude() {
            return center_longitude;
        }

        public void setCenter_longitude(String center_longitude) {
            this.center_longitude = center_longitude;
        }
    }
}
