package com.bingo.hotel.spa.intl.core.api.travelconnect.bean.prebook.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import lombok.Builder;

import java.util.List;

public class PrebookResponse implements BaseResponse {
    private DataBean data;
    private int code;
    private String message;
    private String checkInDate;
    private String checkOutDate;

    public void setCheckInDate(String checkInDate) {
        this.checkInDate = checkInDate;
    }

    public void setCheckOutDate(String checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public String getCheckInDate() {
        return checkInDate;
    }

    public String getCheckOutDate() {
        return checkOutDate;
    }

    public DataBean getData() {
        return data;
    }

    public void setData(DataBean data) {
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public boolean isSucc() {
        return true;
    }

    @Override
    public boolean isEmptyResult() {
        return false;
    }

    public static class DataBean {
        /**
         * roomid : sample string 1
         * roomname : sample string 2
         * bedtypes : [{"bedtypeid":"sample string 1","bedtype":"sample string 2"},{"bedtypeid":"sample string 1","bedtype":"sample string 2"}]
         * hotelinfo : {"hotelcode":"sample string 1","hotelname":"sample string 2","hotelengname":"sample string 3","longitude":"sample string 4","latitude":"sample string 5","rating":"sample string 6","citycode":"sample string 7","city":"sample string 8","country":"sample string 9","countrycode":"sample string 10","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"resort":"sample string 11","address":"sample string 12","streetnumber":"sample string 13","postalcode":"sample string 14","phone":["sample string 1","sample string 2"],"fax":"sample string 15","email":["sample string 1","sample string 2"],"url":"sample string 16","descript":"sample string 17","location":"sample string 18","catedescripts":[{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"},{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"}],"classimages":[{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]},{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]}],"roomtypes":[{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]},{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]}],"hotelfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}],"tripadvisorinfo":{"rating":1.1,"ratingimgurl":"https://www.tripadvisor.com/img/cdsi/img2/ratings/traveler/1.1-52242-5.svg","reviewcount":2,"reviewurl":"sample string 3","nearbyrestaurants":"sample string 4","nearbyattractions":"sample string 5","ranking":{"rankingno":"sample string 1","rankingnumber":"sample string 2","rankinglocation":"sample string 3","rankingdescript":"sample string 4"},"awards":[{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"},{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"}]}}
         * prebookingtoken : sample string 3
         * orgtotal : 4.0
         * hotelfees : [{"description":"sample string 1","amount":2,"currencty":"sample string 3"},{"description":"sample string 1","amount":2,"currencty":"sample string 3"}]
         * surcharges : [{"type":"sample string 1","amount":2,"currencty":"sample string 3"},{"type":"sample string 1","amount":2,"currencty":"sample string 3"}]
         * markuptotal : 5.0
         * markupdescript : sample string 6
         * total : 7.0
         * surchargetotal : 8.0
         * currency : sample string 9
         * cancellations : [{"startdate":"2024-03-20T11:17:29.9764094+08:00","enddate":"2024-03-20T11:17:29.9764094+08:00","penalty":3,"currency":"sample string 4"},{"startdate":"2024-03-20T11:17:29.9764094+08:00","enddate":"2024-03-20T11:17:29.9764094+08:00","penalty":3,"currency":"sample string 4"}]
         * checkininstructions : sample string 10
         * specialcheckininstructions : sample string 11
         * cancellationpolicy : sample string 12
         * roompernights : [{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"},{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"}]
         * additionals : [{"AdditionalId":"sample string 1","Additional":"sample string 2"},{"AdditionalId":"sample string 1","Additional":"sample string 2"}]
         * ispackage : true
         */

        private String roomid;
        private String roomname;
        private HotelinfoBean hotelinfo;
        private String prebookingtoken;
        private double orgtotal;
        private double markuptotal;
        private String markupdescript;
        private double total;
        private double surchargetotal;
        private String currency;
        private String checkininstructions;
        private String specialcheckininstructions;
        private String cancellationpolicy;
        private boolean ispackage;
        private List<BedtypesBean> bedtypes;
        private List<HotelfeesBean> hotelfees;
        private List<SurchargesBean> surcharges;
        private List<CancellationsBean> cancellations;
        private List<RoompernightsBean> roompernights;
        private List<AdditionalsBean> additionals;

        public String getRoomid() {
            return roomid;
        }

        public void setRoomid(String roomid) {
            this.roomid = roomid;
        }

        public String getRoomname() {
            return roomname;
        }

        public void setRoomname(String roomname) {
            this.roomname = roomname;
        }

        public HotelinfoBean getHotelinfo() {
            return hotelinfo;
        }

        public void setHotelinfo(HotelinfoBean hotelinfo) {
            this.hotelinfo = hotelinfo;
        }

        public String getPrebookingtoken() {
            return prebookingtoken;
        }

        public void setPrebookingtoken(String prebookingtoken) {
            this.prebookingtoken = prebookingtoken;
        }

        public double getOrgtotal() {
            return orgtotal;
        }

        public void setOrgtotal(double orgtotal) {
            this.orgtotal = orgtotal;
        }

        public double getMarkuptotal() {
            return markuptotal;
        }

        public void setMarkuptotal(double markuptotal) {
            this.markuptotal = markuptotal;
        }

        public String getMarkupdescript() {
            return markupdescript;
        }

        public void setMarkupdescript(String markupdescript) {
            this.markupdescript = markupdescript;
        }

        public double getTotal() {
            return total;
        }

        public void setTotal(double total) {
            this.total = total;
        }

        public double getSurchargetotal() {
            return surchargetotal;
        }

        public void setSurchargetotal(double surchargetotal) {
            this.surchargetotal = surchargetotal;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public String getCheckininstructions() {
            return checkininstructions;
        }

        public void setCheckininstructions(String checkininstructions) {
            this.checkininstructions = checkininstructions;
        }

        public String getSpecialcheckininstructions() {
            return specialcheckininstructions;
        }

        public void setSpecialcheckininstructions(String specialcheckininstructions) {
            this.specialcheckininstructions = specialcheckininstructions;
        }

        public String getCancellationpolicy() {
            return cancellationpolicy;
        }

        public void setCancellationpolicy(String cancellationpolicy) {
            this.cancellationpolicy = cancellationpolicy;
        }

        public boolean isIspackage() {
            return ispackage;
        }

        public void setIspackage(boolean ispackage) {
            this.ispackage = ispackage;
        }

        public List<BedtypesBean> getBedtypes() {
            return bedtypes;
        }

        public void setBedtypes(List<BedtypesBean> bedtypes) {
            this.bedtypes = bedtypes;
        }

        public List<HotelfeesBean> getHotelfees() {
            return hotelfees;
        }

        public void setHotelfees(List<HotelfeesBean> hotelfees) {
            this.hotelfees = hotelfees;
        }

        public List<SurchargesBean> getSurcharges() {
            return surcharges;
        }

        public void setSurcharges(List<SurchargesBean> surcharges) {
            this.surcharges = surcharges;
        }

        public List<CancellationsBean> getCancellations() {
            return cancellations;
        }

        public void setCancellations(List<CancellationsBean> cancellations) {
            this.cancellations = cancellations;
        }

        public List<RoompernightsBean> getRoompernights() {
            return roompernights;
        }

        public void setRoompernights(List<RoompernightsBean> roompernights) {
            this.roompernights = roompernights;
        }

        public List<AdditionalsBean> getAdditionals() {
            return additionals;
        }

        public void setAdditionals(List<AdditionalsBean> additionals) {
            this.additionals = additionals;
        }

        public static class HotelinfoBean {
            /**
             * hotelcode : sample string 1
             * hotelname : sample string 2
             * hotelengname : sample string 3
             * longitude : sample string 4
             * latitude : sample string 5
             * rating : sample string 6
             * citycode : sample string 7
             * city : sample string 8
             * country : sample string 9
             * countrycode : sample string 10
             * regions : [{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}]
             * resort : sample string 11
             * address : sample string 12
             * streetnumber : sample string 13
             * postalcode : sample string 14
             * phone : ["sample string 1","sample string 2"]
             * fax : sample string 15
             * email : ["sample string 1","sample string 2"]
             * url : sample string 16
             * descript : sample string 17
             * location : sample string 18
             * catedescripts : [{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"},{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"}]
             * classimages : [{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]},{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]}]
             * roomtypes : [{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]},{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]}]
             * hotelfacilitys : [{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]
             * tripadvisorinfo : {"rating":1.1,"ratingimgurl":"https://www.tripadvisor.com/img/cdsi/img2/ratings/traveler/1.1-52242-5.svg","reviewcount":2,"reviewurl":"sample string 3","nearbyrestaurants":"sample string 4","nearbyattractions":"sample string 5","ranking":{"rankingno":"sample string 1","rankingnumber":"sample string 2","rankinglocation":"sample string 3","rankingdescript":"sample string 4"},"awards":[{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"},{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"}]}
             */

            private String hotelcode;
            private String hotelname;
            private String hotelengname;
            private String longitude;
            private String latitude;
            private String rating;
            private String citycode;
            private String city;
            private String country;
            private String countrycode;
            private String resort;
            private String address;
            private String streetnumber;
            private String postalcode;
            private String fax;
            private String url;
            private String descript;
            private String location;
            private TripadvisorinfoBean tripadvisorinfo;
            private List<RegionsBean> regions;
            private List<String> phone;
            private List<String> email;
            private List<CatedescriptsBean> catedescripts;
            private List<ClassimagesBeanX> classimages;
            private List<RoomtypesBean> roomtypes;
            private List<HotelfacilitysBean> hotelfacilitys;

            public String getHotelcode() {
                return hotelcode;
            }

            public void setHotelcode(String hotelcode) {
                this.hotelcode = hotelcode;
            }

            public String getHotelname() {
                return hotelname;
            }

            public void setHotelname(String hotelname) {
                this.hotelname = hotelname;
            }

            public String getHotelengname() {
                return hotelengname;
            }

            public void setHotelengname(String hotelengname) {
                this.hotelengname = hotelengname;
            }

            public String getLongitude() {
                return longitude;
            }

            public void setLongitude(String longitude) {
                this.longitude = longitude;
            }

            public String getLatitude() {
                return latitude;
            }

            public void setLatitude(String latitude) {
                this.latitude = latitude;
            }

            public String getRating() {
                return rating;
            }

            public void setRating(String rating) {
                this.rating = rating;
            }

            public String getCitycode() {
                return citycode;
            }

            public void setCitycode(String citycode) {
                this.citycode = citycode;
            }

            public String getCity() {
                return city;
            }

            public void setCity(String city) {
                this.city = city;
            }

            public String getCountry() {
                return country;
            }

            public void setCountry(String country) {
                this.country = country;
            }

            public String getCountrycode() {
                return countrycode;
            }

            public void setCountrycode(String countrycode) {
                this.countrycode = countrycode;
            }

            public String getResort() {
                return resort;
            }

            public void setResort(String resort) {
                this.resort = resort;
            }

            public String getAddress() {
                return address;
            }

            public void setAddress(String address) {
                this.address = address;
            }

            public String getStreetnumber() {
                return streetnumber;
            }

            public void setStreetnumber(String streetnumber) {
                this.streetnumber = streetnumber;
            }

            public String getPostalcode() {
                return postalcode;
            }

            public void setPostalcode(String postalcode) {
                this.postalcode = postalcode;
            }

            public String getFax() {
                return fax;
            }

            public void setFax(String fax) {
                this.fax = fax;
            }

            public String getUrl() {
                return url;
            }

            public void setUrl(String url) {
                this.url = url;
            }

            public String getDescript() {
                return descript;
            }

            public void setDescript(String descript) {
                this.descript = descript;
            }

            public String getLocation() {
                return location;
            }

            public void setLocation(String location) {
                this.location = location;
            }

            public TripadvisorinfoBean getTripadvisorinfo() {
                return tripadvisorinfo;
            }

            public void setTripadvisorinfo(TripadvisorinfoBean tripadvisorinfo) {
                this.tripadvisorinfo = tripadvisorinfo;
            }

            public List<RegionsBean> getRegions() {
                return regions;
            }

            public void setRegions(List<RegionsBean> regions) {
                this.regions = regions;
            }

            public List<String> getPhone() {
                return phone;
            }

            public void setPhone(List<String> phone) {
                this.phone = phone;
            }

            public List<String> getEmail() {
                return email;
            }

            public void setEmail(List<String> email) {
                this.email = email;
            }

            public List<CatedescriptsBean> getCatedescripts() {
                return catedescripts;
            }

            public void setCatedescripts(List<CatedescriptsBean> catedescripts) {
                this.catedescripts = catedescripts;
            }

            public List<ClassimagesBeanX> getClassimages() {
                return classimages;
            }

            public void setClassimages(List<ClassimagesBeanX> classimages) {
                this.classimages = classimages;
            }

            public List<RoomtypesBean> getRoomtypes() {
                return roomtypes;
            }

            public void setRoomtypes(List<RoomtypesBean> roomtypes) {
                this.roomtypes = roomtypes;
            }

            public List<HotelfacilitysBean> getHotelfacilitys() {
                return hotelfacilitys;
            }

            public void setHotelfacilitys(List<HotelfacilitysBean> hotelfacilitys) {
                this.hotelfacilitys = hotelfacilitys;
            }

            public static class TripadvisorinfoBean {
                /**
                 * rating : 1.1
                 * ratingimgurl : https://www.tripadvisor.com/img/cdsi/img2/ratings/traveler/1.1-52242-5.svg
                 * reviewcount : 2
                 * reviewurl : sample string 3
                 * nearbyrestaurants : sample string 4
                 * nearbyattractions : sample string 5
                 * ranking : {"rankingno":"sample string 1","rankingnumber":"sample string 2","rankinglocation":"sample string 3","rankingdescript":"sample string 4"}
                 * awards : [{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"},{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"}]
                 */

                private double rating;
                private String ratingimgurl;
                private int reviewcount;
                private String reviewurl;
                private String nearbyrestaurants;
                private String nearbyattractions;
                private RankingBean ranking;
                private List<AwardsBean> awards;

                public double getRating() {
                    return rating;
                }

                public void setRating(double rating) {
                    this.rating = rating;
                }

                public String getRatingimgurl() {
                    return ratingimgurl;
                }

                public void setRatingimgurl(String ratingimgurl) {
                    this.ratingimgurl = ratingimgurl;
                }

                public int getReviewcount() {
                    return reviewcount;
                }

                public void setReviewcount(int reviewcount) {
                    this.reviewcount = reviewcount;
                }

                public String getReviewurl() {
                    return reviewurl;
                }

                public void setReviewurl(String reviewurl) {
                    this.reviewurl = reviewurl;
                }

                public String getNearbyrestaurants() {
                    return nearbyrestaurants;
                }

                public void setNearbyrestaurants(String nearbyrestaurants) {
                    this.nearbyrestaurants = nearbyrestaurants;
                }

                public String getNearbyattractions() {
                    return nearbyattractions;
                }

                public void setNearbyattractions(String nearbyattractions) {
                    this.nearbyattractions = nearbyattractions;
                }

                public RankingBean getRanking() {
                    return ranking;
                }

                public void setRanking(RankingBean ranking) {
                    this.ranking = ranking;
                }

                public List<AwardsBean> getAwards() {
                    return awards;
                }

                public void setAwards(List<AwardsBean> awards) {
                    this.awards = awards;
                }

                public static class RankingBean {
                    /**
                     * rankingno : sample string 1
                     * rankingnumber : sample string 2
                     * rankinglocation : sample string 3
                     * rankingdescript : sample string 4
                     */

                    private String rankingno;
                    private String rankingnumber;
                    private String rankinglocation;
                    private String rankingdescript;

                    public String getRankingno() {
                        return rankingno;
                    }

                    public void setRankingno(String rankingno) {
                        this.rankingno = rankingno;
                    }

                    public String getRankingnumber() {
                        return rankingnumber;
                    }

                    public void setRankingnumber(String rankingnumber) {
                        this.rankingnumber = rankingnumber;
                    }

                    public String getRankinglocation() {
                        return rankinglocation;
                    }

                    public void setRankinglocation(String rankinglocation) {
                        this.rankinglocation = rankinglocation;
                    }

                    public String getRankingdescript() {
                        return rankingdescript;
                    }

                    public void setRankingdescript(String rankingdescript) {
                        this.rankingdescript = rankingdescript;
                    }
                }

                public static class AwardsBean {
                    /**
                     * awardtype : sample string 1
                     * displayname : sample string 2
                     * year : sample string 3
                     * tinyimgurl : sample string 4
                     * smallimgurl : sample string 5
                     * largeimgurl : sample string 6
                     */

                    private String awardtype;
                    private String displayname;
                    private String year;
                    private String tinyimgurl;
                    private String smallimgurl;
                    private String largeimgurl;

                    public String getAwardtype() {
                        return awardtype;
                    }

                    public void setAwardtype(String awardtype) {
                        this.awardtype = awardtype;
                    }

                    public String getDisplayname() {
                        return displayname;
                    }

                    public void setDisplayname(String displayname) {
                        this.displayname = displayname;
                    }

                    public String getYear() {
                        return year;
                    }

                    public void setYear(String year) {
                        this.year = year;
                    }

                    public String getTinyimgurl() {
                        return tinyimgurl;
                    }

                    public void setTinyimgurl(String tinyimgurl) {
                        this.tinyimgurl = tinyimgurl;
                    }

                    public String getSmallimgurl() {
                        return smallimgurl;
                    }

                    public void setSmallimgurl(String smallimgurl) {
                        this.smallimgurl = smallimgurl;
                    }

                    public String getLargeimgurl() {
                        return largeimgurl;
                    }

                    public void setLargeimgurl(String largeimgurl) {
                        this.largeimgurl = largeimgurl;
                    }
                }
            }

            public static class RegionsBean {
                /**
                 * regionid : sample string 1
                 * region : sample string 2
                 */

                private String regionid;
                private String region;

                public String getRegionid() {
                    return regionid;
                }

                public void setRegionid(String regionid) {
                    this.regionid = regionid;
                }

                public String getRegion() {
                    return region;
                }

                public void setRegion(String region) {
                    this.region = region;
                }
            }

            public static class CatedescriptsBean {
                /**
                 * cateid : sample string 1
                 * catename : sample string 2
                 * content : sample string 3
                 */

                private String cateid;
                private String catename;
                private String content;

                public String getCateid() {
                    return cateid;
                }

                public void setCateid(String cateid) {
                    this.cateid = cateid;
                }

                public String getCatename() {
                    return catename;
                }

                public void setCatename(String catename) {
                    this.catename = catename;
                }

                public String getContent() {
                    return content;
                }

                public void setContent(String content) {
                    this.content = content;
                }
            }

            public static class ClassimagesBeanX {
                /**
                 * classid : sample string 1
                 * classname : sample string 2
                 * classimages : [{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]
                 */

                private String classid;
                private String classname;
                private List<ClassimagesBean> classimages;

                public String getClassid() {
                    return classid;
                }

                public void setClassid(String classid) {
                    this.classid = classid;
                }

                public String getClassname() {
                    return classname;
                }

                public void setClassname(String classname) {
                    this.classname = classname;
                }

                public List<ClassimagesBean> getClassimages() {
                    return classimages;
                }

                public void setClassimages(List<ClassimagesBean> classimages) {
                    this.classimages = classimages;
                }

                public static class ClassimagesBean {
                    /**
                     * title : sample string 1
                     * url : sample string 2
                     * descript : sample string 3
                     */

                    private String title;
                    private String url;
                    private String descript;

                    public String getTitle() {
                        return title;
                    }

                    public void setTitle(String title) {
                        this.title = title;
                    }

                    public String getUrl() {
                        return url;
                    }

                    public void setUrl(String url) {
                        this.url = url;
                    }

                    public String getDescript() {
                        return descript;
                    }

                    public void setDescript(String descript) {
                        this.descript = descript;
                    }
                }
            }

            public static class RoomtypesBean {
                /**
                 * roomtypeid : sample string 1
                 * roomtype : sample string 2
                 * roomfacilitys : [{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]
                 */

                private String roomtypeid;
                private String roomtype;
                private List<RoomfacilitysBean> roomfacilitys;

                public String getRoomtypeid() {
                    return roomtypeid;
                }

                public void setRoomtypeid(String roomtypeid) {
                    this.roomtypeid = roomtypeid;
                }

                public String getRoomtype() {
                    return roomtype;
                }

                public void setRoomtype(String roomtype) {
                    this.roomtype = roomtype;
                }

                public List<RoomfacilitysBean> getRoomfacilitys() {
                    return roomfacilitys;
                }

                public void setRoomfacilitys(List<RoomfacilitysBean> roomfacilitys) {
                    this.roomfacilitys = roomfacilitys;
                }

                public static class RoomfacilitysBean {
                    /**
                     * facilityid : sample string 1
                     * facility : sample string 2
                     * notes : sample string 3
                     * costinfo : sample string 4
                     */

                    private String facilityid;
                    private String facility;
                    private String notes;
                    private String costinfo;

                    public String getFacilityid() {
                        return facilityid;
                    }

                    public void setFacilityid(String facilityid) {
                        this.facilityid = facilityid;
                    }

                    public String getFacility() {
                        return facility;
                    }

                    public void setFacility(String facility) {
                        this.facility = facility;
                    }

                    public String getNotes() {
                        return notes;
                    }

                    public void setNotes(String notes) {
                        this.notes = notes;
                    }

                    public String getCostinfo() {
                        return costinfo;
                    }

                    public void setCostinfo(String costinfo) {
                        this.costinfo = costinfo;
                    }
                }
            }

            public static class HotelfacilitysBean {
                /**
                 * facilityid : sample string 1
                 * facility : sample string 2
                 * notes : sample string 3
                 * costinfo : sample string 4
                 */

                private String facilityid;
                private String facility;
                private String notes;
                private String costinfo;

                public String getFacilityid() {
                    return facilityid;
                }

                public void setFacilityid(String facilityid) {
                    this.facilityid = facilityid;
                }

                public String getFacility() {
                    return facility;
                }

                public void setFacility(String facility) {
                    this.facility = facility;
                }

                public String getNotes() {
                    return notes;
                }

                public void setNotes(String notes) {
                    this.notes = notes;
                }

                public String getCostinfo() {
                    return costinfo;
                }

                public void setCostinfo(String costinfo) {
                    this.costinfo = costinfo;
                }
            }
        }

        public static class BedtypesBean {
            /**
             * bedtypeid : sample string 1
             * bedtype : sample string 2
             */

            private String bedtypeid;
            private String bedtype;

            public String getBedtypeid() {
                return bedtypeid;
            }

            public void setBedtypeid(String bedtypeid) {
                this.bedtypeid = bedtypeid;
            }

            public String getBedtype() {
                return bedtype;
            }

            public void setBedtype(String bedtype) {
                this.bedtype = bedtype;
            }
        }

        public static class HotelfeesBean {
            /**
             * description : sample string 1
             * amount : 2.0
             * currencty : sample string 3
             */

            private String description;
            private double amount;
            private String currencty;

            public String getDescription() {
                return description;
            }

            public void setDescription(String description) {
                this.description = description;
            }

            public double getAmount() {
                return amount;
            }

            public void setAmount(double amount) {
                this.amount = amount;
            }

            public String getCurrencty() {
                return currencty;
            }

            public void setCurrencty(String currencty) {
                this.currencty = currencty;
            }
        }

        public static class SurchargesBean {
            /**
             * type : sample string 1
             * amount : 2.0
             * currencty : sample string 3
             */

            private String type;
            private double amount;
            private String currencty;

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public double getAmount() {
                return amount;
            }

            public void setAmount(double amount) {
                this.amount = amount;
            }

            public String getCurrencty() {
                return currencty;
            }

            public void setCurrencty(String currencty) {
                this.currencty = currencty;
            }
        }

        public static class CancellationsBean {
            /**
             * startdate : 2024-03-20T11:17:29.9764094+08:00
             * enddate : 2024-03-20T11:17:29.9764094+08:00
             * penalty : 3.0
             * currency : sample string 4
             */

            private String startdate;
            private String enddate;
            private double penalty;
            private String currency;

            public String getStartdate() {
                return startdate;
            }

            public void setStartdate(String startdate) {
                this.startdate = startdate;
            }

            public String getEnddate() {
                return enddate;
            }

            public void setEnddate(String enddate) {
                this.enddate = enddate;
            }

            public double getPenalty() {
                return penalty;
            }

            public void setPenalty(double penalty) {
                this.penalty = penalty;
            }

            public String getCurrency() {
                return currency;
            }

            public void setCurrency(String currency) {
                this.currency = currency;
            }
        }

        public static class RoompernightsBean {
            /**
             * date : sample string 1
             * orgrate : 2.0
             * rate : 3.0
             * currency : sample string 4
             */

            private String date;
            private double orgrate;
            private double rate;
            private String currency;

            public String getDate() {
                return date;
            }

            public void setDate(String date) {
                this.date = date;
            }

            public double getOrgrate() {
                return orgrate;
            }

            public void setOrgrate(double orgrate) {
                this.orgrate = orgrate;
            }

            public double getRate() {
                return rate;
            }

            public void setRate(double rate) {
                this.rate = rate;
            }

            public String getCurrency() {
                return currency;
            }

            public void setCurrency(String currency) {
                this.currency = currency;
            }
        }

        public static class AdditionalsBean {
            /**
             * AdditionalId : sample string 1
             * Additional : sample string 2
             */

            private String AdditionalId;
            private String Additional;

            public String getAdditionalId() {
                return AdditionalId;
            }

            public void setAdditionalId(String AdditionalId) {
                this.AdditionalId = AdditionalId;
            }

            public String getAdditional() {
                return Additional;
            }

            public void setAdditional(String Additional) {
                this.Additional = Additional;
            }
        }
    }
}
