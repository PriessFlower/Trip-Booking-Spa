package com.trip.booking.spa.legacy.aichotels.bean.price.availability;

import com.trip.booking.spa.legacy.aichotels.bean.price.prebook.PreBookResponse;
import com.trip.booking.spa.platform.http.asynchttp.BaseResponse;

import java.util.List;

public class AvailabilityResponse implements BaseResponse {

    /**
     * result : {"return_status":{"success":"true","exception":""}}
     * room_list : [{"room_name":"1 King Bed, Non-smoking, High Speed Internet Access, Coffee Maker, Hairdryer, Iron And Ironing Board","room_desc":"","room_features":"","room_type":"2090939","rates_and_cancellation_policies":[{"rate_plan_code":"LP1","meal_plan_code":"RO","total_amount_before_tax":"132.30","total_amount_after_tax":"155.48","currency":"USD","rates":[{"check_in":"2020-07-27","check_out":"2020-07-28","rooms":1,"amount_before_tax":{"night_rate":"132.30","sub_total":"132.30"},"amount_after_tax":{"night_rate":"155.48","sub_total":"155.48"}}],"cancellation_information":{"support_cancel":"yes","non_refundable":"no","details":[{"datetime":"2020-07-24T00:00:00","fee_type_value":"1","fee_type":"nights","amount_penalty":"155.48","policy_code":"CXP"},{"fee_type_value":"1","fee_type":"nights","amount_penalty":"155.48","policy_code":"CNS"}],"timezone":"America/Toronto UTC-05:00"},"room_key":"MjA5MDkzOS4uKkxQMS4uKjUzMzY1","rate_comments":"The rate includes only room and sales tax. Incidentals are not included.","nationality":"|","breakfast":{"include":0,"count":0},"meal_plan_desc":"\u201cRoom Only\u201d, \u201cIt only includes the stay for all guests in selected room. No meals are included.\u201d"}],"bed_info":null}]
     */

    private ResultBean result;
    private List<RoomListBean> room_list;
    private String hotelCode;
    private PreBookResponse preBookResponse;

    public void setPreBookResponse(PreBookResponse preBookResponse) {
        this.preBookResponse = preBookResponse;
    }

    public PreBookResponse getPreBookResponse() {
        return preBookResponse;
    }

    public String getHotelCode() {
        return hotelCode;
    }

    public void setHotelCode(String hotelCode) {
        this.hotelCode = hotelCode;
    }

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
         * room_name : 1 King Bed, Non-smoking, High Speed Internet Access, Coffee Maker, Hairdryer, Iron And Ironing Board
         * room_desc :
         * room_features :
         * room_type : 2090939
         * rates_and_cancellation_policies : [{"rate_plan_code":"LP1","meal_plan_code":"RO","total_amount_before_tax":"132.30","total_amount_after_tax":"155.48","currency":"USD","rates":[{"check_in":"2020-07-27","check_out":"2020-07-28","rooms":1,"amount_before_tax":{"night_rate":"132.30","sub_total":"132.30"},"amount_after_tax":{"night_rate":"155.48","sub_total":"155.48"}}],"cancellation_information":{"support_cancel":"yes","non_refundable":"no","details":[{"datetime":"2020-07-24T00:00:00","fee_type_value":"1","fee_type":"nights","amount_penalty":"155.48","policy_code":"CXP"},{"fee_type_value":"1","fee_type":"nights","amount_penalty":"155.48","policy_code":"CNS"}],"timezone":"America/Toronto UTC-05:00"},"room_key":"MjA5MDkzOS4uKkxQMS4uKjUzMzY1","rate_comments":"The rate includes only room and sales tax. Incidentals are not included.","nationality":"|","breakfast":{"include":0,"count":0},"meal_plan_desc":"\u201cRoom Only\u201d, \u201cIt only includes the stay for all guests in selected room. No meals are included.\u201d"}]
         * bed_info : null
         */

        private String room_name;
        private String room_desc;
        private String room_features;
        private String room_type;
        private Object bed_info;
        private List<RatesAndCancellationPoliciesBean> rates_and_cancellation_policies;

        public String getRoom_name() {
            return room_name;
        }

        public void setRoom_name(String room_name) {
            this.room_name = room_name;
        }

        public String getRoom_desc() {
            return room_desc;
        }

        public void setRoom_desc(String room_desc) {
            this.room_desc = room_desc;
        }

        public String getRoom_features() {
            return room_features;
        }

        public void setRoom_features(String room_features) {
            this.room_features = room_features;
        }

        public String getRoom_type() {
            return room_type;
        }

        public void setRoom_type(String room_type) {
            this.room_type = room_type;
        }

        public Object getBed_info() {
            return bed_info;
        }

        public void setBed_info(Object bed_info) {
            this.bed_info = bed_info;
        }

        public List<RatesAndCancellationPoliciesBean> getRates_and_cancellation_policies() {
            return rates_and_cancellation_policies;
        }

        public void setRates_and_cancellation_policies(List<RatesAndCancellationPoliciesBean> rates_and_cancellation_policies) {
            this.rates_and_cancellation_policies = rates_and_cancellation_policies;
        }

        public static class RatesAndCancellationPoliciesBean {
            /**
             * rate_plan_code : LP1
             * meal_plan_code : RO
             * total_amount_before_tax : 132.30
             * total_amount_after_tax : 155.48
             * currency : USD
             * rates : [{"check_in":"2020-07-27","check_out":"2020-07-28","rooms":1,"amount_before_tax":{"night_rate":"132.30","sub_total":"132.30"},"amount_after_tax":{"night_rate":"155.48","sub_total":"155.48"}}]
             * cancellation_information : {"support_cancel":"yes","non_refundable":"no","details":[{"datetime":"2020-07-24T00:00:00","fee_type_value":"1","fee_type":"nights","amount_penalty":"155.48","policy_code":"CXP"},{"fee_type_value":"1","fee_type":"nights","amount_penalty":"155.48","policy_code":"CNS"}],"timezone":"America/Toronto UTC-05:00"}
             * room_key : MjA5MDkzOS4uKkxQMS4uKjUzMzY1
             * rate_comments : The rate includes only room and sales tax. Incidentals are not included.
             * nationality : |
             * breakfast : {"include":0,"count":0}
             * meal_plan_desc : “Room Only”, “It only includes the stay for all guests in selected room. No meals are included.”
             */

            private String rate_plan_code;
            private String meal_plan_code;
            private String total_amount_before_tax;
            private String total_amount_after_tax;
            private String currency;
            private CancellationInformationBean cancellation_information;
            private String room_key;
            private String rate_comments;
            private String nationality;
            private BreakfastBean breakfast;
            private String meal_plan_desc;
            private List<RatesBean> rates;

            public String getRate_plan_code() {
                return rate_plan_code;
            }

            public void setRate_plan_code(String rate_plan_code) {
                this.rate_plan_code = rate_plan_code;
            }

            public String getMeal_plan_code() {
                return meal_plan_code;
            }

            public void setMeal_plan_code(String meal_plan_code) {
                this.meal_plan_code = meal_plan_code;
            }

            public String getTotal_amount_before_tax() {
                return total_amount_before_tax;
            }

            public void setTotal_amount_before_tax(String total_amount_before_tax) {
                this.total_amount_before_tax = total_amount_before_tax;
            }

            public String getTotal_amount_after_tax() {
                return total_amount_after_tax;
            }

            public void setTotal_amount_after_tax(String total_amount_after_tax) {
                this.total_amount_after_tax = total_amount_after_tax;
            }

            public String getCurrency() {
                return currency;
            }

            public void setCurrency(String currency) {
                this.currency = currency;
            }

            public CancellationInformationBean getCancellation_information() {
                return cancellation_information;
            }

            public void setCancellation_information(CancellationInformationBean cancellation_information) {
                this.cancellation_information = cancellation_information;
            }

            public String getRoom_key() {
                return room_key;
            }

            public void setRoom_key(String room_key) {
                this.room_key = room_key;
            }

            public String getRate_comments() {
                return rate_comments;
            }

            public void setRate_comments(String rate_comments) {
                this.rate_comments = rate_comments;
            }

            public String getNationality() {
                return nationality;
            }

            public void setNationality(String nationality) {
                this.nationality = nationality;
            }

            public BreakfastBean getBreakfast() {
                return breakfast;
            }

            public void setBreakfast(BreakfastBean breakfast) {
                this.breakfast = breakfast;
            }

            public String getMeal_plan_desc() {
                return meal_plan_desc;
            }

            public void setMeal_plan_desc(String meal_plan_desc) {
                this.meal_plan_desc = meal_plan_desc;
            }

            public List<RatesBean> getRates() {
                return rates;
            }

            public void setRates(List<RatesBean> rates) {
                this.rates = rates;
            }

            public static class CancellationInformationBean {
                /**
                 * support_cancel : yes
                 * non_refundable : no
                 * details : [{"datetime":"2020-07-24T00:00:00","fee_type_value":"1","fee_type":"nights","amount_penalty":"155.48","policy_code":"CXP"},{"fee_type_value":"1","fee_type":"nights","amount_penalty":"155.48","policy_code":"CNS"}]
                 * timezone : America/Toronto UTC-05:00
                 */

                private String support_cancel;
                private String non_refundable;
                private String timezone;
                private List<DetailsBean> details;

                public String getSupport_cancel() {
                    return support_cancel;
                }

                public void setSupport_cancel(String support_cancel) {
                    this.support_cancel = support_cancel;
                }

                public String getNon_refundable() {
                    return non_refundable;
                }

                public void setNon_refundable(String non_refundable) {
                    this.non_refundable = non_refundable;
                }

                public String getTimezone() {
                    return timezone;
                }

                public void setTimezone(String timezone) {
                    this.timezone = timezone;
                }

                public List<DetailsBean> getDetails() {
                    return details;
                }

                public void setDetails(List<DetailsBean> details) {
                    this.details = details;
                }

                public static class DetailsBean {
                    /**
                     * datetime : 2020-07-24T00:00:00
                     * fee_type_value : 1
                     * fee_type : nights
                     * amount_penalty : 155.48
                     * policy_code : CXP
                     */

                    private String datetime;
                    private String fee_type_value;
                    private String fee_type;
                    private String amount_penalty;
                    private String policy_code;

                    public String getDatetime() {
                        return datetime;
                    }

                    public void setDatetime(String datetime) {
                        this.datetime = datetime;
                    }

                    public String getFee_type_value() {
                        return fee_type_value;
                    }

                    public void setFee_type_value(String fee_type_value) {
                        this.fee_type_value = fee_type_value;
                    }

                    public String getFee_type() {
                        return fee_type;
                    }

                    public void setFee_type(String fee_type) {
                        this.fee_type = fee_type;
                    }

                    public String getAmount_penalty() {
                        return amount_penalty;
                    }

                    public void setAmount_penalty(String amount_penalty) {
                        this.amount_penalty = amount_penalty;
                    }

                    public String getPolicy_code() {
                        return policy_code;
                    }

                    public void setPolicy_code(String policy_code) {
                        this.policy_code = policy_code;
                    }
                }
            }

            public static class BreakfastBean {
                /**
                 * include : 0
                 * count : 0
                 */

                private int include;
                private int count;

                public int getInclude() {
                    return include;
                }

                public void setInclude(int include) {
                    this.include = include;
                }

                public int getCount() {
                    return count;
                }

                public void setCount(int count) {
                    this.count = count;
                }
            }

            public static class RatesBean {
                /**
                 * check_in : 2020-07-27
                 * check_out : 2020-07-28
                 * rooms : 1
                 * amount_before_tax : {"night_rate":"132.30","sub_total":"132.30"}
                 * amount_after_tax : {"night_rate":"155.48","sub_total":"155.48"}
                 */

                private String check_in;
                private String check_out;
                private int rooms;
                private AmountBeforeTaxBean amount_before_tax;
                private AmountAfterTaxBean amount_after_tax;

                public String getCheck_in() {
                    return check_in;
                }

                public void setCheck_in(String check_in) {
                    this.check_in = check_in;
                }

                public String getCheck_out() {
                    return check_out;
                }

                public void setCheck_out(String check_out) {
                    this.check_out = check_out;
                }

                public int getRooms() {
                    return rooms;
                }

                public void setRooms(int rooms) {
                    this.rooms = rooms;
                }

                public AmountBeforeTaxBean getAmount_before_tax() {
                    return amount_before_tax;
                }

                public void setAmount_before_tax(AmountBeforeTaxBean amount_before_tax) {
                    this.amount_before_tax = amount_before_tax;
                }

                public AmountAfterTaxBean getAmount_after_tax() {
                    return amount_after_tax;
                }

                public void setAmount_after_tax(AmountAfterTaxBean amount_after_tax) {
                    this.amount_after_tax = amount_after_tax;
                }

                public static class AmountBeforeTaxBean {
                    /**
                     * night_rate : 132.30
                     * sub_total : 132.30
                     */

                    private String night_rate;
                    private String sub_total;

                    public String getNight_rate() {
                        return night_rate;
                    }

                    public void setNight_rate(String night_rate) {
                        this.night_rate = night_rate;
                    }

                    public String getSub_total() {
                        return sub_total;
                    }

                    public void setSub_total(String sub_total) {
                        this.sub_total = sub_total;
                    }
                }

                public static class AmountAfterTaxBean {
                    /**
                     * night_rate : 155.48
                     * sub_total : 155.48
                     */

                    private String night_rate;
                    private String sub_total;

                    public String getNight_rate() {
                        return night_rate;
                    }

                    public void setNight_rate(String night_rate) {
                        this.night_rate = night_rate;
                    }

                    public String getSub_total() {
                        return sub_total;
                    }

                    public void setSub_total(String sub_total) {
                        this.sub_total = sub_total;
                    }
                }
            }
        }
    }
}
