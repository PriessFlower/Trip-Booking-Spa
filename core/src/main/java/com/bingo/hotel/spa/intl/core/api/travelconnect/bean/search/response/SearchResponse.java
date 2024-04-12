package com.bingo.hotel.spa.intl.core.api.travelconnect.bean.search.response;

import com.bingo.hotel.spa.intl.core.api.common.asynchttp.BaseResponse;
import com.bingo.hotel.spa.intl.core.api.travelconnect.bean.prebook.response.PrebookResponse;

import java.util.List;

public class SearchResponse implements BaseResponse {

    /**
     * data : {"pagehotellist":{"total_count":1,"page":2,"page_size":3,"data_list":[{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]},{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]}],"page_count":1,"skip":4},"specialhotellist":[{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]},{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]}],"hoteldetail":{"location":"sample string 1","streetnumber":"sample string 2","country":"sample string 3","countrycode":"sample string 4","citycode":"sample string 5","city":"sample string 6","resort":"sample string 7","postalcode":"sample string 8","phone":["sample string 1","sample string 2"],"fax":"sample string 9","email":["sample string 1","sample string 2"],"url":"sample string 10","catedescripts":[{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"},{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"}],"classimages":[{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]},{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]}],"roomfacilist":[{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]},{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]}],"checkininstructions":"sample string 11","checkinspecialinstructions":"sample string 12","rooms":[{"roomid":"sample string 1","roomname":"sample string 2","groupid":"sample string 3","groupname":"sample string 4","plansid":"sample string 5","status":"sample string 6","description":"sample string 7","allotment":8,"orgtotal":9,"markuptotal":10,"markupdescript":"sample string 11","total":12,"currency":"sample string 13","includebreakfast":true,"freewifi":true,"adultcount":16,"childcount":17,"promotions":[{"name":"sample string 1","description":"sample string 2"},{"name":"sample string 1","description":"sample string 2"}],"warns":[{"WarnTitle":"sample string 1","Descript":"sample string 2"},{"WarnTitle":"sample string 1","Descript":"sample string 2"}],"availableoptions":[{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true},{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true}],"roompernights":[{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"},{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"}],"roomimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}],"additionals":[{"AdditionalId":"sample string 1","Additional":"sample string 2"},{"AdditionalId":"sample string 1","Additional":"sample string 2"}],"smokingpreferences":"sample string 18","bedtypes":[{"bedtypeid":"sample string 1","bedtype":"sample string 2"},{"bedtypeid":"sample string 1","bedtype":"sample string 2"}],"cancellationpolicy":"sample string 19","ispackage":true},{"roomid":"sample string 1","roomname":"sample string 2","groupid":"sample string 3","groupname":"sample string 4","plansid":"sample string 5","status":"sample string 6","description":"sample string 7","allotment":8,"orgtotal":9,"markuptotal":10,"markupdescript":"sample string 11","total":12,"currency":"sample string 13","includebreakfast":true,"freewifi":true,"adultcount":16,"childcount":17,"promotions":[{"name":"sample string 1","description":"sample string 2"},{"name":"sample string 1","description":"sample string 2"}],"warns":[{"WarnTitle":"sample string 1","Descript":"sample string 2"},{"WarnTitle":"sample string 1","Descript":"sample string 2"}],"availableoptions":[{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true},{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true}],"roompernights":[{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"},{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"}],"roomimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}],"additionals":[{"AdditionalId":"sample string 1","Additional":"sample string 2"},{"AdditionalId":"sample string 1","Additional":"sample string 2"}],"smokingpreferences":"sample string 18","bedtypes":[{"bedtypeid":"sample string 1","bedtype":"sample string 2"},{"bedtypeid":"sample string 1","bedtype":"sample string 2"}],"cancellationpolicy":"sample string 19","ispackage":true}],"roomgroups":[{"groupid":"sample string 1","groupname":"sample string 2","groupdescript":"sample string 3","groupimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}]},{"groupid":"sample string 1","groupname":"sample string 2","groupdescript":"sample string 3","groupimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}]}],"tripadvisorinfo":{"rating":1.1,"ratingimgurl":"sample string 2","reviewcount":3,"reviewurl":"sample string 4","nearbyrestaurants":"sample string 5","nearbyattractions":"sample string 6","ranking":{"rankingno":"sample string 1","rankingnumber":"sample string 2","rankinglocation":"sample string 3","rankingdescript":"sample string 4"},"awards":[{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"},{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"}]},"hotelcode":"sample string 14","hotelname":"sample string 15","hotelengname":"sample string 16","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":17,"tripadrating":18.1,"tripadratingimgurl":"sample string 19","tripadreviewcount":20,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 21","longitude":"sample string 22","latitude":"sample string 23","paytotal":24,"currency":"sample string 25","coverpic":"sample string 26","orderby":27,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]},"hotelsummary":{"starlist":[{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"regionlist":[{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"tripadratinglist":[{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"facilitylist":[{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"landmarklist":[{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"hotellocations":[{"hotelcode":"sample string 1","hotelname":"sample string 2","hotelengname":"sample string 3","star":4,"tripadrating":5.1,"tripadratingimgurl":"sample string 6","tripadreviewcount":7,"lng":"sample string 8","Lat":"sample string 9","price":10},{"hotelcode":"sample string 1","hotelname":"sample string 2","hotelengname":"sample string 3","star":4,"tripadrating":5.1,"tripadratingimgurl":"sample string 6","tripadreviewcount":7,"lng":"sample string 8","Lat":"sample string 9","price":10}],"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"minprice":1,"maxprice":2},"citycode":"sample string 1","cityname":"sample string 2","searchguid":"sample string 3","landmarkguid":"sample string 4","iscompleted":true}
     * code : 1
     * message : sample string 2
     */

    private DataBean data;
    private int code;
    private String message;
    private String checkInDate;
    private String checkOutDate;
    private String plansId;
    private PrebookResponse prebookResponse;

    public String getPlansId() {
        return plansId;
    }

    public void setPlansId(String plansId) {
        this.plansId = plansId;
    }

    public void setPrebookResponse(PrebookResponse prebookResponse) {
        this.prebookResponse = prebookResponse;
    }

    public PrebookResponse getPrebookResponse() {
        return prebookResponse;
    }

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
         * pagehotellist : {"total_count":1,"page":2,"page_size":3,"data_list":[{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]},{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]}],"page_count":1,"skip":4}
         * specialhotellist : [{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]},{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]}]
         * hoteldetail : {"location":"sample string 1","streetnumber":"sample string 2","country":"sample string 3","countrycode":"sample string 4","citycode":"sample string 5","city":"sample string 6","resort":"sample string 7","postalcode":"sample string 8","phone":["sample string 1","sample string 2"],"fax":"sample string 9","email":["sample string 1","sample string 2"],"url":"sample string 10","catedescripts":[{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"},{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"}],"classimages":[{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]},{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]}],"roomfacilist":[{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]},{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]}],"checkininstructions":"sample string 11","checkinspecialinstructions":"sample string 12","rooms":[{"roomid":"sample string 1","roomname":"sample string 2","groupid":"sample string 3","groupname":"sample string 4","plansid":"sample string 5","status":"sample string 6","description":"sample string 7","allotment":8,"orgtotal":9,"markuptotal":10,"markupdescript":"sample string 11","total":12,"currency":"sample string 13","includebreakfast":true,"freewifi":true,"adultcount":16,"childcount":17,"promotions":[{"name":"sample string 1","description":"sample string 2"},{"name":"sample string 1","description":"sample string 2"}],"warns":[{"WarnTitle":"sample string 1","Descript":"sample string 2"},{"WarnTitle":"sample string 1","Descript":"sample string 2"}],"availableoptions":[{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true},{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true}],"roompernights":[{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"},{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"}],"roomimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}],"additionals":[{"AdditionalId":"sample string 1","Additional":"sample string 2"},{"AdditionalId":"sample string 1","Additional":"sample string 2"}],"smokingpreferences":"sample string 18","bedtypes":[{"bedtypeid":"sample string 1","bedtype":"sample string 2"},{"bedtypeid":"sample string 1","bedtype":"sample string 2"}],"cancellationpolicy":"sample string 19","ispackage":true},{"roomid":"sample string 1","roomname":"sample string 2","groupid":"sample string 3","groupname":"sample string 4","plansid":"sample string 5","status":"sample string 6","description":"sample string 7","allotment":8,"orgtotal":9,"markuptotal":10,"markupdescript":"sample string 11","total":12,"currency":"sample string 13","includebreakfast":true,"freewifi":true,"adultcount":16,"childcount":17,"promotions":[{"name":"sample string 1","description":"sample string 2"},{"name":"sample string 1","description":"sample string 2"}],"warns":[{"WarnTitle":"sample string 1","Descript":"sample string 2"},{"WarnTitle":"sample string 1","Descript":"sample string 2"}],"availableoptions":[{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true},{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true}],"roompernights":[{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"},{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"}],"roomimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}],"additionals":[{"AdditionalId":"sample string 1","Additional":"sample string 2"},{"AdditionalId":"sample string 1","Additional":"sample string 2"}],"smokingpreferences":"sample string 18","bedtypes":[{"bedtypeid":"sample string 1","bedtype":"sample string 2"},{"bedtypeid":"sample string 1","bedtype":"sample string 2"}],"cancellationpolicy":"sample string 19","ispackage":true}],"roomgroups":[{"groupid":"sample string 1","groupname":"sample string 2","groupdescript":"sample string 3","groupimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}]},{"groupid":"sample string 1","groupname":"sample string 2","groupdescript":"sample string 3","groupimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}]}],"tripadvisorinfo":{"rating":1.1,"ratingimgurl":"sample string 2","reviewcount":3,"reviewurl":"sample string 4","nearbyrestaurants":"sample string 5","nearbyattractions":"sample string 6","ranking":{"rankingno":"sample string 1","rankingnumber":"sample string 2","rankinglocation":"sample string 3","rankingdescript":"sample string 4"},"awards":[{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"},{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"}]},"hotelcode":"sample string 14","hotelname":"sample string 15","hotelengname":"sample string 16","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":17,"tripadrating":18.1,"tripadratingimgurl":"sample string 19","tripadreviewcount":20,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 21","longitude":"sample string 22","latitude":"sample string 23","paytotal":24,"currency":"sample string 25","coverpic":"sample string 26","orderby":27,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]}
         * hotelsummary : {"starlist":[{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"regionlist":[{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"tripadratinglist":[{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"facilitylist":[{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"landmarklist":[{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}],"hotellocations":[{"hotelcode":"sample string 1","hotelname":"sample string 2","hotelengname":"sample string 3","star":4,"tripadrating":5.1,"tripadratingimgurl":"sample string 6","tripadreviewcount":7,"lng":"sample string 8","Lat":"sample string 9","price":10},{"hotelcode":"sample string 1","hotelname":"sample string 2","hotelengname":"sample string 3","star":4,"tripadrating":5.1,"tripadratingimgurl":"sample string 6","tripadreviewcount":7,"lng":"sample string 8","Lat":"sample string 9","price":10}],"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"minprice":1,"maxprice":2}
         * citycode : sample string 1
         * cityname : sample string 2
         * searchguid : sample string 3
         * landmarkguid : sample string 4
         * iscompleted : true
         */

        private PagehotellistBean pagehotellist;
        private HoteldetailBean hoteldetail;
        private HotelsummaryBean hotelsummary;
        private String citycode;
        private String cityname;
        private String searchguid;
        private String landmarkguid;
        private boolean iscompleted;
        private List<SpecialhotellistBean> specialhotellist;

        public PagehotellistBean getPagehotellist() {
            return pagehotellist;
        }

        public void setPagehotellist(PagehotellistBean pagehotellist) {
            this.pagehotellist = pagehotellist;
        }

        public HoteldetailBean getHoteldetail() {
            return hoteldetail;
        }

        public void setHoteldetail(HoteldetailBean hoteldetail) {
            this.hoteldetail = hoteldetail;
        }

        public HotelsummaryBean getHotelsummary() {
            return hotelsummary;
        }

        public void setHotelsummary(HotelsummaryBean hotelsummary) {
            this.hotelsummary = hotelsummary;
        }

        public String getCitycode() {
            return citycode;
        }

        public void setCitycode(String citycode) {
            this.citycode = citycode;
        }

        public String getCityname() {
            return cityname;
        }

        public void setCityname(String cityname) {
            this.cityname = cityname;
        }

        public String getSearchguid() {
            return searchguid;
        }

        public void setSearchguid(String searchguid) {
            this.searchguid = searchguid;
        }

        public String getLandmarkguid() {
            return landmarkguid;
        }

        public void setLandmarkguid(String landmarkguid) {
            this.landmarkguid = landmarkguid;
        }

        public boolean isIscompleted() {
            return iscompleted;
        }

        public void setIscompleted(boolean iscompleted) {
            this.iscompleted = iscompleted;
        }

        public List<SpecialhotellistBean> getSpecialhotellist() {
            return specialhotellist;
        }

        public void setSpecialhotellist(List<SpecialhotellistBean> specialhotellist) {
            this.specialhotellist = specialhotellist;
        }

        public static class PagehotellistBean {
            /**
             * total_count : 1
             * page : 2
             * page_size : 3
             * data_list : [{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]},{"hotelcode":"sample string 2","hotelname":"sample string 3","hotelengname":"sample string 4","regions":[{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}],"star":5,"tripadrating":6.1,"tripadratingimgurl":"sample string 7","tripadreviewcount":8,"triptypes":[{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}],"address":["sample string 1","sample string 2"],"descript":"sample string 9","longitude":"sample string 10","latitude":"sample string 11","paytotal":12,"currency":"sample string 13","coverpic":"sample string 14","orderby":15,"hotelfacilist":[{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]}]
             * page_count : 1
             * skip : 4
             */

            private int total_count;
            private int page;
            private int page_size;
            private int page_count;
            private int skip;
            private List<DataListBean> data_list;

            public int getTotal_count() {
                return total_count;
            }

            public void setTotal_count(int total_count) {
                this.total_count = total_count;
            }

            public int getPage() {
                return page;
            }

            public void setPage(int page) {
                this.page = page;
            }

            public int getPage_size() {
                return page_size;
            }

            public void setPage_size(int page_size) {
                this.page_size = page_size;
            }

            public int getPage_count() {
                return page_count;
            }

            public void setPage_count(int page_count) {
                this.page_count = page_count;
            }

            public int getSkip() {
                return skip;
            }

            public void setSkip(int skip) {
                this.skip = skip;
            }

            public List<DataListBean> getData_list() {
                return data_list;
            }

            public void setData_list(List<DataListBean> data_list) {
                this.data_list = data_list;
            }

            public static class DataListBean {
                /**
                 * hotelcode : sample string 2
                 * hotelname : sample string 3
                 * hotelengname : sample string 4
                 * regions : [{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}]
                 * star : 5.0
                 * tripadrating : 6.1
                 * tripadratingimgurl : sample string 7
                 * tripadreviewcount : 8
                 * triptypes : [{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}]
                 * address : ["sample string 1","sample string 2"]
                 * descript : sample string 9
                 * longitude : sample string 10
                 * latitude : sample string 11
                 * paytotal : 12.0
                 * currency : sample string 13
                 * coverpic : sample string 14
                 * orderby : 15
                 * hotelfacilist : [{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]
                 */

                private String hotelcode;
                private String hotelname;
                private String hotelengname;
                private double star;
                private double tripadrating;
                private String tripadratingimgurl;
                private int tripadreviewcount;
                private String descript;
                private String longitude;
                private String latitude;
                private double paytotal;
                private String currency;
                private String coverpic;
                private int orderby;
                private List<RegionsBean> regions;
                private List<TriptypesBean> triptypes;
                private List<String> address;
                private List<HotelfacilistBean> hotelfacilist;

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

                public double getStar() {
                    return star;
                }

                public void setStar(double star) {
                    this.star = star;
                }

                public double getTripadrating() {
                    return tripadrating;
                }

                public void setTripadrating(double tripadrating) {
                    this.tripadrating = tripadrating;
                }

                public String getTripadratingimgurl() {
                    return tripadratingimgurl;
                }

                public void setTripadratingimgurl(String tripadratingimgurl) {
                    this.tripadratingimgurl = tripadratingimgurl;
                }

                public int getTripadreviewcount() {
                    return tripadreviewcount;
                }

                public void setTripadreviewcount(int tripadreviewcount) {
                    this.tripadreviewcount = tripadreviewcount;
                }

                public String getDescript() {
                    return descript;
                }

                public void setDescript(String descript) {
                    this.descript = descript;
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

                public double getPaytotal() {
                    return paytotal;
                }

                public void setPaytotal(double paytotal) {
                    this.paytotal = paytotal;
                }

                public String getCurrency() {
                    return currency;
                }

                public void setCurrency(String currency) {
                    this.currency = currency;
                }

                public String getCoverpic() {
                    return coverpic;
                }

                public void setCoverpic(String coverpic) {
                    this.coverpic = coverpic;
                }

                public int getOrderby() {
                    return orderby;
                }

                public void setOrderby(int orderby) {
                    this.orderby = orderby;
                }

                public List<RegionsBean> getRegions() {
                    return regions;
                }

                public void setRegions(List<RegionsBean> regions) {
                    this.regions = regions;
                }

                public List<TriptypesBean> getTriptypes() {
                    return triptypes;
                }

                public void setTriptypes(List<TriptypesBean> triptypes) {
                    this.triptypes = triptypes;
                }

                public List<String> getAddress() {
                    return address;
                }

                public void setAddress(List<String> address) {
                    this.address = address;
                }

                public List<HotelfacilistBean> getHotelfacilist() {
                    return hotelfacilist;
                }

                public void setHotelfacilist(List<HotelfacilistBean> hotelfacilist) {
                    this.hotelfacilist = hotelfacilist;
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

                public static class TriptypesBean {
                    /**
                     * hotelid : sample string 1
                     * triptype : sample string 2
                     * reviewcount : 3
                     */

                    private String hotelid;
                    private String triptype;
                    private int reviewcount;

                    public String getHotelid() {
                        return hotelid;
                    }

                    public void setHotelid(String hotelid) {
                        this.hotelid = hotelid;
                    }

                    public String getTriptype() {
                        return triptype;
                    }

                    public void setTriptype(String triptype) {
                        this.triptype = triptype;
                    }

                    public int getReviewcount() {
                        return reviewcount;
                    }

                    public void setReviewcount(int reviewcount) {
                        this.reviewcount = reviewcount;
                    }
                }

                public static class HotelfacilistBean {
                    /**
                     * iconname : sample string 1
                     * facilityid : sample string 2
                     * facility : sample string 3
                     * notes : sample string 4
                     * costinfo : sample string 5
                     */

                    private String iconname;
                    private String facilityid;
                    private String facility;
                    private String notes;
                    private String costinfo;

                    public String getIconname() {
                        return iconname;
                    }

                    public void setIconname(String iconname) {
                        this.iconname = iconname;
                    }

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
        }

        public static class HoteldetailBean {
            /**
             * location : sample string 1
             * streetnumber : sample string 2
             * country : sample string 3
             * countrycode : sample string 4
             * citycode : sample string 5
             * city : sample string 6
             * resort : sample string 7
             * postalcode : sample string 8
             * phone : ["sample string 1","sample string 2"]
             * fax : sample string 9
             * email : ["sample string 1","sample string 2"]
             * url : sample string 10
             * catedescripts : [{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"},{"cateid":"sample string 1","catename":"sample string 2","content":"sample string 3"}]
             * classimages : [{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]},{"classid":"sample string 1","classname":"sample string 2","classimages":[{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"},{"title":"sample string 1","url":"sample string 2","descript":"sample string 3"}]}]
             * roomfacilist : [{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]},{"roomtypeid":"sample string 1","roomtype":"sample string 2","roomfacilitys":[{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"},{"facilityid":"sample string 1","facility":"sample string 2","notes":"sample string 3","costinfo":"sample string 4"}]}]
             * checkininstructions : sample string 11
             * checkinspecialinstructions : sample string 12
             * rooms : [{"roomid":"sample string 1","roomname":"sample string 2","groupid":"sample string 3","groupname":"sample string 4","plansid":"sample string 5","status":"sample string 6","description":"sample string 7","allotment":8,"orgtotal":9,"markuptotal":10,"markupdescript":"sample string 11","total":12,"currency":"sample string 13","includebreakfast":true,"freewifi":true,"adultcount":16,"childcount":17,"promotions":[{"name":"sample string 1","description":"sample string 2"},{"name":"sample string 1","description":"sample string 2"}],"warns":[{"WarnTitle":"sample string 1","Descript":"sample string 2"},{"WarnTitle":"sample string 1","Descript":"sample string 2"}],"availableoptions":[{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true},{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true}],"roompernights":[{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"},{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"}],"roomimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}],"additionals":[{"AdditionalId":"sample string 1","Additional":"sample string 2"},{"AdditionalId":"sample string 1","Additional":"sample string 2"}],"smokingpreferences":"sample string 18","bedtypes":[{"bedtypeid":"sample string 1","bedtype":"sample string 2"},{"bedtypeid":"sample string 1","bedtype":"sample string 2"}],"cancellationpolicy":"sample string 19","ispackage":true},{"roomid":"sample string 1","roomname":"sample string 2","groupid":"sample string 3","groupname":"sample string 4","plansid":"sample string 5","status":"sample string 6","description":"sample string 7","allotment":8,"orgtotal":9,"markuptotal":10,"markupdescript":"sample string 11","total":12,"currency":"sample string 13","includebreakfast":true,"freewifi":true,"adultcount":16,"childcount":17,"promotions":[{"name":"sample string 1","description":"sample string 2"},{"name":"sample string 1","description":"sample string 2"}],"warns":[{"WarnTitle":"sample string 1","Descript":"sample string 2"},{"WarnTitle":"sample string 1","Descript":"sample string 2"}],"availableoptions":[{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true},{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true}],"roompernights":[{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"},{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"}],"roomimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}],"additionals":[{"AdditionalId":"sample string 1","Additional":"sample string 2"},{"AdditionalId":"sample string 1","Additional":"sample string 2"}],"smokingpreferences":"sample string 18","bedtypes":[{"bedtypeid":"sample string 1","bedtype":"sample string 2"},{"bedtypeid":"sample string 1","bedtype":"sample string 2"}],"cancellationpolicy":"sample string 19","ispackage":true}]
             * roomgroups : [{"groupid":"sample string 1","groupname":"sample string 2","groupdescript":"sample string 3","groupimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}]},{"groupid":"sample string 1","groupname":"sample string 2","groupdescript":"sample string 3","groupimages":[{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}]}]
             * tripadvisorinfo : {"rating":1.1,"ratingimgurl":"sample string 2","reviewcount":3,"reviewurl":"sample string 4","nearbyrestaurants":"sample string 5","nearbyattractions":"sample string 6","ranking":{"rankingno":"sample string 1","rankingnumber":"sample string 2","rankinglocation":"sample string 3","rankingdescript":"sample string 4"},"awards":[{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"},{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"}]}
             * hotelcode : sample string 14
             * hotelname : sample string 15
             * hotelengname : sample string 16
             * regions : [{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}]
             * star : 17.0
             * tripadrating : 18.1
             * tripadratingimgurl : sample string 19
             * tripadreviewcount : 20
             * triptypes : [{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}]
             * address : ["sample string 1","sample string 2"]
             * descript : sample string 21
             * longitude : sample string 22
             * latitude : sample string 23
             * paytotal : 24.0
             * currency : sample string 25
             * coverpic : sample string 26
             * orderby : 27
             * hotelfacilist : [{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]
             */

            private String location;
            private String streetnumber;
            private String country;
            private String countrycode;
            private String citycode;
            private String city;
            private String resort;
            private String postalcode;
            private String fax;
            private String url;
            private String checkininstructions;
            private String checkinspecialinstructions;
            private TripadvisorinfoBean tripadvisorinfo;
            private String hotelcode;
            private String hotelname;
            private String hotelengname;
            private double star;
            private double tripadrating;
            private String tripadratingimgurl;
            private int tripadreviewcount;
            private String descript;
            private String longitude;
            private String latitude;
            private double paytotal;
            private String currency;
            private String coverpic;
            private int orderby;
            private List<String> phone;
            private List<String> email;
            private List<CatedescriptsBean> catedescripts;
            private List<ClassimagesBeanX> classimages;
            private List<RoomfacilistBean> roomfacilist;
            private List<RoomsBean> rooms;
            private List<RoomgroupsBean> roomgroups;
            private List<RegionsBeanX> regions;
            private List<TriptypesBeanX> triptypes;
            private List<String> address;
            private List<HotelfacilistBeanX> hotelfacilist;

            public String getLocation() {
                return location;
            }

            public void setLocation(String location) {
                this.location = location;
            }

            public String getStreetnumber() {
                return streetnumber;
            }

            public void setStreetnumber(String streetnumber) {
                this.streetnumber = streetnumber;
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

            public String getResort() {
                return resort;
            }

            public void setResort(String resort) {
                this.resort = resort;
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

            public String getCheckininstructions() {
                return checkininstructions;
            }

            public void setCheckininstructions(String checkininstructions) {
                this.checkininstructions = checkininstructions;
            }

            public String getCheckinspecialinstructions() {
                return checkinspecialinstructions;
            }

            public void setCheckinspecialinstructions(String checkinspecialinstructions) {
                this.checkinspecialinstructions = checkinspecialinstructions;
            }

            public TripadvisorinfoBean getTripadvisorinfo() {
                return tripadvisorinfo;
            }

            public void setTripadvisorinfo(TripadvisorinfoBean tripadvisorinfo) {
                this.tripadvisorinfo = tripadvisorinfo;
            }

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

            public double getStar() {
                return star;
            }

            public void setStar(double star) {
                this.star = star;
            }

            public double getTripadrating() {
                return tripadrating;
            }

            public void setTripadrating(double tripadrating) {
                this.tripadrating = tripadrating;
            }

            public String getTripadratingimgurl() {
                return tripadratingimgurl;
            }

            public void setTripadratingimgurl(String tripadratingimgurl) {
                this.tripadratingimgurl = tripadratingimgurl;
            }

            public int getTripadreviewcount() {
                return tripadreviewcount;
            }

            public void setTripadreviewcount(int tripadreviewcount) {
                this.tripadreviewcount = tripadreviewcount;
            }

            public String getDescript() {
                return descript;
            }

            public void setDescript(String descript) {
                this.descript = descript;
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

            public double getPaytotal() {
                return paytotal;
            }

            public void setPaytotal(double paytotal) {
                this.paytotal = paytotal;
            }

            public String getCurrency() {
                return currency;
            }

            public void setCurrency(String currency) {
                this.currency = currency;
            }

            public String getCoverpic() {
                return coverpic;
            }

            public void setCoverpic(String coverpic) {
                this.coverpic = coverpic;
            }

            public int getOrderby() {
                return orderby;
            }

            public void setOrderby(int orderby) {
                this.orderby = orderby;
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

            public List<RoomfacilistBean> getRoomfacilist() {
                return roomfacilist;
            }

            public void setRoomfacilist(List<RoomfacilistBean> roomfacilist) {
                this.roomfacilist = roomfacilist;
            }

            public List<RoomsBean> getRooms() {
                return rooms;
            }

            public void setRooms(List<RoomsBean> rooms) {
                this.rooms = rooms;
            }

            public List<RoomgroupsBean> getRoomgroups() {
                return roomgroups;
            }

            public void setRoomgroups(List<RoomgroupsBean> roomgroups) {
                this.roomgroups = roomgroups;
            }

            public List<RegionsBeanX> getRegions() {
                return regions;
            }

            public void setRegions(List<RegionsBeanX> regions) {
                this.regions = regions;
            }

            public List<TriptypesBeanX> getTriptypes() {
                return triptypes;
            }

            public void setTriptypes(List<TriptypesBeanX> triptypes) {
                this.triptypes = triptypes;
            }

            public List<String> getAddress() {
                return address;
            }

            public void setAddress(List<String> address) {
                this.address = address;
            }

            public List<HotelfacilistBeanX> getHotelfacilist() {
                return hotelfacilist;
            }

            public void setHotelfacilist(List<HotelfacilistBeanX> hotelfacilist) {
                this.hotelfacilist = hotelfacilist;
            }

            public static class TripadvisorinfoBean {
                /**
                 * rating : 1.1
                 * ratingimgurl : sample string 2
                 * reviewcount : 3
                 * reviewurl : sample string 4
                 * nearbyrestaurants : sample string 5
                 * nearbyattractions : sample string 6
                 * ranking : {"rankingno":"sample string 1","rankingnumber":"sample string 2","rankinglocation":"sample string 3","rankingdescript":"sample string 4"}
                 * awards : [{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"},{"awardtype":"sample string 1","displayname":"sample string 2","year":"sample string 3","tinyimgurl":"sample string 4","smallimgurl":"sample string 5","largeimgurl":"sample string 6"}]
                 */

                private String rating;
                private String ratingimgurl;
                private int reviewcount;
                private String reviewurl;
                private String nearbyrestaurants;
                private String nearbyattractions;
                private RankingBean ranking;
                private List<AwardsBean> awards;

                public String getRating() {
                    return rating;
                }

                public void setRating(String rating) {
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

            public static class RoomfacilistBean {
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

            public static class RoomsBean {
                /**
                 * roomid : sample string 1
                 * roomname : sample string 2
                 * groupid : sample string 3
                 * groupname : sample string 4
                 * plansid : sample string 5
                 * status : sample string 6
                 * description : sample string 7
                 * allotment : 8
                 * orgtotal : 9.0
                 * markuptotal : 10.0
                 * markupdescript : sample string 11
                 * total : 12.0
                 * currency : sample string 13
                 * includebreakfast : true
                 * freewifi : true
                 * adultcount : 16
                 * childcount : 17
                 * promotions : [{"name":"sample string 1","description":"sample string 2"},{"name":"sample string 1","description":"sample string 2"}]
                 * warns : [{"WarnTitle":"sample string 1","Descript":"sample string 2"},{"WarnTitle":"sample string 1","Descript":"sample string 2"}]
                 * availableoptions : [{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true},{"optionid":"sample string 1","optionname":"sample string 2","rate":3,"currency":"sample string 4","compulsory":true}]
                 * roompernights : [{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"},{"date":"sample string 1","orgrate":2,"rate":3,"currency":"sample string 4"}]
                 * roomimages : [{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}]
                 * additionals : [{"AdditionalId":"sample string 1","Additional":"sample string 2"},{"AdditionalId":"sample string 1","Additional":"sample string 2"}]
                 * smokingpreferences : sample string 18
                 * bedtypes : [{"bedtypeid":"sample string 1","bedtype":"sample string 2"},{"bedtypeid":"sample string 1","bedtype":"sample string 2"}]
                 * cancellationpolicy : sample string 19
                 * ispackage : true
                 */

                private String roomid;
                private String roomname;
                private String groupid;
                private String groupname;
                private String plansid;
                private String status;
                private String description;
                private int allotment;
                private double orgtotal;
                private double markuptotal;
                private String markupdescript;
                private double total;
                private String currency;
                private boolean includebreakfast;
                private boolean freewifi;
                private int adultcount;
                private int childcount;
                private String smokingpreferences;
                private String cancellationpolicy;
                private boolean ispackage;
                private List<PromotionsBean> promotions;
                private List<WarnsBean> warns;
                private List<AvailableoptionsBean> availableoptions;
                private List<RoompernightsBean> roompernights;
                private List<RoomimagesBean> roomimages;
                private List<AdditionalsBean> additionals;
                private List<BedtypesBean> bedtypes;

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

                public String getGroupid() {
                    return groupid;
                }

                public void setGroupid(String groupid) {
                    this.groupid = groupid;
                }

                public String getGroupname() {
                    return groupname;
                }

                public void setGroupname(String groupname) {
                    this.groupname = groupname;
                }

                public String getPlansid() {
                    return plansid;
                }

                public void setPlansid(String plansid) {
                    this.plansid = plansid;
                }

                public String getStatus() {
                    return status;
                }

                public void setStatus(String status) {
                    this.status = status;
                }

                public String getDescription() {
                    return description;
                }

                public void setDescription(String description) {
                    this.description = description;
                }

                public int getAllotment() {
                    return allotment;
                }

                public void setAllotment(int allotment) {
                    this.allotment = allotment;
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

                public String getCurrency() {
                    return currency;
                }

                public void setCurrency(String currency) {
                    this.currency = currency;
                }

                public boolean isIncludebreakfast() {
                    return includebreakfast;
                }

                public void setIncludebreakfast(boolean includebreakfast) {
                    this.includebreakfast = includebreakfast;
                }

                public boolean isFreewifi() {
                    return freewifi;
                }

                public void setFreewifi(boolean freewifi) {
                    this.freewifi = freewifi;
                }

                public int getAdultcount() {
                    return adultcount;
                }

                public void setAdultcount(int adultcount) {
                    this.adultcount = adultcount;
                }

                public int getChildcount() {
                    return childcount;
                }

                public void setChildcount(int childcount) {
                    this.childcount = childcount;
                }

                public String getSmokingpreferences() {
                    return smokingpreferences;
                }

                public void setSmokingpreferences(String smokingpreferences) {
                    this.smokingpreferences = smokingpreferences;
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

                public List<PromotionsBean> getPromotions() {
                    return promotions;
                }

                public void setPromotions(List<PromotionsBean> promotions) {
                    this.promotions = promotions;
                }

                public List<WarnsBean> getWarns() {
                    return warns;
                }

                public void setWarns(List<WarnsBean> warns) {
                    this.warns = warns;
                }

                public List<AvailableoptionsBean> getAvailableoptions() {
                    return availableoptions;
                }

                public void setAvailableoptions(List<AvailableoptionsBean> availableoptions) {
                    this.availableoptions = availableoptions;
                }

                public List<RoompernightsBean> getRoompernights() {
                    return roompernights;
                }

                public void setRoompernights(List<RoompernightsBean> roompernights) {
                    this.roompernights = roompernights;
                }

                public List<RoomimagesBean> getRoomimages() {
                    return roomimages;
                }

                public void setRoomimages(List<RoomimagesBean> roomimages) {
                    this.roomimages = roomimages;
                }

                public List<AdditionalsBean> getAdditionals() {
                    return additionals;
                }

                public void setAdditionals(List<AdditionalsBean> additionals) {
                    this.additionals = additionals;
                }

                public List<BedtypesBean> getBedtypes() {
                    return bedtypes;
                }

                public void setBedtypes(List<BedtypesBean> bedtypes) {
                    this.bedtypes = bedtypes;
                }

                public static class PromotionsBean {
                    /**
                     * name : sample string 1
                     * description : sample string 2
                     */

                    private String name;
                    private String description;

                    public String getName() {
                        return name;
                    }

                    public void setName(String name) {
                        this.name = name;
                    }

                    public String getDescription() {
                        return description;
                    }

                    public void setDescription(String description) {
                        this.description = description;
                    }
                }

                public static class WarnsBean {
                    /**
                     * WarnTitle : sample string 1
                     * Descript : sample string 2
                     */

                    private String WarnTitle;
                    private String Descript;

                    public String getWarnTitle() {
                        return WarnTitle;
                    }

                    public void setWarnTitle(String WarnTitle) {
                        this.WarnTitle = WarnTitle;
                    }

                    public String getDescript() {
                        return Descript;
                    }

                    public void setDescript(String Descript) {
                        this.Descript = Descript;
                    }
                }

                public static class AvailableoptionsBean {
                    /**
                     * optionid : sample string 1
                     * optionname : sample string 2
                     * rate : 3.0
                     * currency : sample string 4
                     * compulsory : true
                     */

                    private String optionid;
                    private String optionname;
                    private double rate;
                    private String currency;
                    private boolean compulsory;

                    public String getOptionid() {
                        return optionid;
                    }

                    public void setOptionid(String optionid) {
                        this.optionid = optionid;
                    }

                    public String getOptionname() {
                        return optionname;
                    }

                    public void setOptionname(String optionname) {
                        this.optionname = optionname;
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

                    public boolean isCompulsory() {
                        return compulsory;
                    }

                    public void setCompulsory(boolean compulsory) {
                        this.compulsory = compulsory;
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

                public static class RoomimagesBean {
                    /**
                     * url : sample string 1
                     * title : sample string 2
                     * descript : sample string 3
                     * isprimary : true
                     */

                    private String url;
                    private String title;
                    private String descript;
                    private boolean isprimary;

                    public String getUrl() {
                        return url;
                    }

                    public void setUrl(String url) {
                        this.url = url;
                    }

                    public String getTitle() {
                        return title;
                    }

                    public void setTitle(String title) {
                        this.title = title;
                    }

                    public String getDescript() {
                        return descript;
                    }

                    public void setDescript(String descript) {
                        this.descript = descript;
                    }

                    public boolean isIsprimary() {
                        return isprimary;
                    }

                    public void setIsprimary(boolean isprimary) {
                        this.isprimary = isprimary;
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
            }

            public static class RoomgroupsBean {
                /**
                 * groupid : sample string 1
                 * groupname : sample string 2
                 * groupdescript : sample string 3
                 * groupimages : [{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true},{"url":"sample string 1","title":"sample string 2","descript":"sample string 3","isprimary":true}]
                 */

                private String groupid;
                private String groupname;
                private String groupdescript;
                private List<GroupimagesBean> groupimages;

                public String getGroupid() {
                    return groupid;
                }

                public void setGroupid(String groupid) {
                    this.groupid = groupid;
                }

                public String getGroupname() {
                    return groupname;
                }

                public void setGroupname(String groupname) {
                    this.groupname = groupname;
                }

                public String getGroupdescript() {
                    return groupdescript;
                }

                public void setGroupdescript(String groupdescript) {
                    this.groupdescript = groupdescript;
                }

                public List<GroupimagesBean> getGroupimages() {
                    return groupimages;
                }

                public void setGroupimages(List<GroupimagesBean> groupimages) {
                    this.groupimages = groupimages;
                }

                public static class GroupimagesBean {
                    /**
                     * url : sample string 1
                     * title : sample string 2
                     * descript : sample string 3
                     * isprimary : true
                     */

                    private String url;
                    private String title;
                    private String descript;
                    private boolean isprimary;

                    public String getUrl() {
                        return url;
                    }

                    public void setUrl(String url) {
                        this.url = url;
                    }

                    public String getTitle() {
                        return title;
                    }

                    public void setTitle(String title) {
                        this.title = title;
                    }

                    public String getDescript() {
                        return descript;
                    }

                    public void setDescript(String descript) {
                        this.descript = descript;
                    }

                    public boolean isIsprimary() {
                        return isprimary;
                    }

                    public void setIsprimary(boolean isprimary) {
                        this.isprimary = isprimary;
                    }
                }
            }

            public static class RegionsBeanX {
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

            public static class TriptypesBeanX {
                /**
                 * hotelid : sample string 1
                 * triptype : sample string 2
                 * reviewcount : 3
                 */

                private String hotelid;
                private String triptype;
                private int reviewcount;

                public String getHotelid() {
                    return hotelid;
                }

                public void setHotelid(String hotelid) {
                    this.hotelid = hotelid;
                }

                public String getTriptype() {
                    return triptype;
                }

                public void setTriptype(String triptype) {
                    this.triptype = triptype;
                }

                public int getReviewcount() {
                    return reviewcount;
                }

                public void setReviewcount(int reviewcount) {
                    this.reviewcount = reviewcount;
                }
            }

            public static class HotelfacilistBeanX {
                /**
                 * iconname : sample string 1
                 * facilityid : sample string 2
                 * facility : sample string 3
                 * notes : sample string 4
                 * costinfo : sample string 5
                 */

                private String iconname;
                private String facilityid;
                private String facility;
                private String notes;
                private String costinfo;

                public String getIconname() {
                    return iconname;
                }

                public void setIconname(String iconname) {
                    this.iconname = iconname;
                }

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

        public static class HotelsummaryBean {
            /**
             * starlist : [{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}]
             * regionlist : [{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}]
             * tripadratinglist : [{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}]
             * facilitylist : [{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}]
             * landmarklist : [{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"},{"hotelcodes":["sample string 1","sample string 2"],"summaryitemid":"sample string 1","summaryitemcount":2,"summaryitemname":"sample string 3"}]
             * hotellocations : [{"hotelcode":"sample string 1","hotelname":"sample string 2","hotelengname":"sample string 3","star":4,"tripadrating":5.1,"tripadratingimgurl":"sample string 6","tripadreviewcount":7,"lng":"sample string 8","Lat":"sample string 9","price":10},{"hotelcode":"sample string 1","hotelname":"sample string 2","hotelengname":"sample string 3","star":4,"tripadrating":5.1,"tripadratingimgurl":"sample string 6","tripadreviewcount":7,"lng":"sample string 8","Lat":"sample string 9","price":10}]
             * triptypes : [{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}]
             * minprice : 1.0
             * maxprice : 2.0
             */

            private double minprice;
            private double maxprice;
            private List<StarlistBean> starlist;
            private List<RegionlistBean> regionlist;
            private List<TripadratinglistBean> tripadratinglist;
            private List<FacilitylistBean> facilitylist;
            private List<LandmarklistBean> landmarklist;
            private List<HotellocationsBean> hotellocations;
            private List<TriptypesBeanXX> triptypes;

            public double getMinprice() {
                return minprice;
            }

            public void setMinprice(double minprice) {
                this.minprice = minprice;
            }

            public double getMaxprice() {
                return maxprice;
            }

            public void setMaxprice(double maxprice) {
                this.maxprice = maxprice;
            }

            public List<StarlistBean> getStarlist() {
                return starlist;
            }

            public void setStarlist(List<StarlistBean> starlist) {
                this.starlist = starlist;
            }

            public List<RegionlistBean> getRegionlist() {
                return regionlist;
            }

            public void setRegionlist(List<RegionlistBean> regionlist) {
                this.regionlist = regionlist;
            }

            public List<TripadratinglistBean> getTripadratinglist() {
                return tripadratinglist;
            }

            public void setTripadratinglist(List<TripadratinglistBean> tripadratinglist) {
                this.tripadratinglist = tripadratinglist;
            }

            public List<FacilitylistBean> getFacilitylist() {
                return facilitylist;
            }

            public void setFacilitylist(List<FacilitylistBean> facilitylist) {
                this.facilitylist = facilitylist;
            }

            public List<LandmarklistBean> getLandmarklist() {
                return landmarklist;
            }

            public void setLandmarklist(List<LandmarklistBean> landmarklist) {
                this.landmarklist = landmarklist;
            }

            public List<HotellocationsBean> getHotellocations() {
                return hotellocations;
            }

            public void setHotellocations(List<HotellocationsBean> hotellocations) {
                this.hotellocations = hotellocations;
            }

            public List<TriptypesBeanXX> getTriptypes() {
                return triptypes;
            }

            public void setTriptypes(List<TriptypesBeanXX> triptypes) {
                this.triptypes = triptypes;
            }

            public static class StarlistBean {
                /**
                 * summaryitemid : sample string 1
                 * summaryitemcount : 2
                 * summaryitemname : sample string 3
                 */

                private String summaryitemid;
                private int summaryitemcount;
                private String summaryitemname;

                public String getSummaryitemid() {
                    return summaryitemid;
                }

                public void setSummaryitemid(String summaryitemid) {
                    this.summaryitemid = summaryitemid;
                }

                public int getSummaryitemcount() {
                    return summaryitemcount;
                }

                public void setSummaryitemcount(int summaryitemcount) {
                    this.summaryitemcount = summaryitemcount;
                }

                public String getSummaryitemname() {
                    return summaryitemname;
                }

                public void setSummaryitemname(String summaryitemname) {
                    this.summaryitemname = summaryitemname;
                }
            }

            public static class RegionlistBean {
                /**
                 * summaryitemid : sample string 1
                 * summaryitemcount : 2
                 * summaryitemname : sample string 3
                 */

                private String summaryitemid;
                private int summaryitemcount;
                private String summaryitemname;

                public String getSummaryitemid() {
                    return summaryitemid;
                }

                public void setSummaryitemid(String summaryitemid) {
                    this.summaryitemid = summaryitemid;
                }

                public int getSummaryitemcount() {
                    return summaryitemcount;
                }

                public void setSummaryitemcount(int summaryitemcount) {
                    this.summaryitemcount = summaryitemcount;
                }

                public String getSummaryitemname() {
                    return summaryitemname;
                }

                public void setSummaryitemname(String summaryitemname) {
                    this.summaryitemname = summaryitemname;
                }
            }

            public static class TripadratinglistBean {
                /**
                 * summaryitemid : sample string 1
                 * summaryitemcount : 2
                 * summaryitemname : sample string 3
                 */

                private String summaryitemid;
                private int summaryitemcount;
                private String summaryitemname;

                public String getSummaryitemid() {
                    return summaryitemid;
                }

                public void setSummaryitemid(String summaryitemid) {
                    this.summaryitemid = summaryitemid;
                }

                public int getSummaryitemcount() {
                    return summaryitemcount;
                }

                public void setSummaryitemcount(int summaryitemcount) {
                    this.summaryitemcount = summaryitemcount;
                }

                public String getSummaryitemname() {
                    return summaryitemname;
                }

                public void setSummaryitemname(String summaryitemname) {
                    this.summaryitemname = summaryitemname;
                }
            }

            public static class FacilitylistBean {
                /**
                 * hotelcodes : ["sample string 1","sample string 2"]
                 * summaryitemid : sample string 1
                 * summaryitemcount : 2
                 * summaryitemname : sample string 3
                 */

                private String summaryitemid;
                private int summaryitemcount;
                private String summaryitemname;
                private List<String> hotelcodes;

                public String getSummaryitemid() {
                    return summaryitemid;
                }

                public void setSummaryitemid(String summaryitemid) {
                    this.summaryitemid = summaryitemid;
                }

                public int getSummaryitemcount() {
                    return summaryitemcount;
                }

                public void setSummaryitemcount(int summaryitemcount) {
                    this.summaryitemcount = summaryitemcount;
                }

                public String getSummaryitemname() {
                    return summaryitemname;
                }

                public void setSummaryitemname(String summaryitemname) {
                    this.summaryitemname = summaryitemname;
                }

                public List<String> getHotelcodes() {
                    return hotelcodes;
                }

                public void setHotelcodes(List<String> hotelcodes) {
                    this.hotelcodes = hotelcodes;
                }
            }

            public static class LandmarklistBean {
                /**
                 * hotelcodes : ["sample string 1","sample string 2"]
                 * summaryitemid : sample string 1
                 * summaryitemcount : 2
                 * summaryitemname : sample string 3
                 */

                private String summaryitemid;
                private int summaryitemcount;
                private String summaryitemname;
                private List<String> hotelcodes;

                public String getSummaryitemid() {
                    return summaryitemid;
                }

                public void setSummaryitemid(String summaryitemid) {
                    this.summaryitemid = summaryitemid;
                }

                public int getSummaryitemcount() {
                    return summaryitemcount;
                }

                public void setSummaryitemcount(int summaryitemcount) {
                    this.summaryitemcount = summaryitemcount;
                }

                public String getSummaryitemname() {
                    return summaryitemname;
                }

                public void setSummaryitemname(String summaryitemname) {
                    this.summaryitemname = summaryitemname;
                }

                public List<String> getHotelcodes() {
                    return hotelcodes;
                }

                public void setHotelcodes(List<String> hotelcodes) {
                    this.hotelcodes = hotelcodes;
                }
            }

            public static class HotellocationsBean {
                /**
                 * hotelcode : sample string 1
                 * hotelname : sample string 2
                 * hotelengname : sample string 3
                 * star : 4.0
                 * tripadrating : 5.1
                 * tripadratingimgurl : sample string 6
                 * tripadreviewcount : 7
                 * lng : sample string 8
                 * Lat : sample string 9
                 * price : 10.0
                 */

                private String hotelcode;
                private String hotelname;
                private String hotelengname;
                private double star;
                private double tripadrating;
                private String tripadratingimgurl;
                private int tripadreviewcount;
                private String lng;
                private String Lat;
                private double price;

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

                public double getStar() {
                    return star;
                }

                public void setStar(double star) {
                    this.star = star;
                }

                public double getTripadrating() {
                    return tripadrating;
                }

                public void setTripadrating(double tripadrating) {
                    this.tripadrating = tripadrating;
                }

                public String getTripadratingimgurl() {
                    return tripadratingimgurl;
                }

                public void setTripadratingimgurl(String tripadratingimgurl) {
                    this.tripadratingimgurl = tripadratingimgurl;
                }

                public int getTripadreviewcount() {
                    return tripadreviewcount;
                }

                public void setTripadreviewcount(int tripadreviewcount) {
                    this.tripadreviewcount = tripadreviewcount;
                }

                public String getLng() {
                    return lng;
                }

                public void setLng(String lng) {
                    this.lng = lng;
                }

                public String getLat() {
                    return Lat;
                }

                public void setLat(String Lat) {
                    this.Lat = Lat;
                }

                public double getPrice() {
                    return price;
                }

                public void setPrice(double price) {
                    this.price = price;
                }
            }

            public static class TriptypesBeanXX {
                /**
                 * hotelid : sample string 1
                 * triptype : sample string 2
                 * reviewcount : 3
                 */

                private String hotelid;
                private String triptype;
                private int reviewcount;

                public String getHotelid() {
                    return hotelid;
                }

                public void setHotelid(String hotelid) {
                    this.hotelid = hotelid;
                }

                public String getTriptype() {
                    return triptype;
                }

                public void setTriptype(String triptype) {
                    this.triptype = triptype;
                }

                public int getReviewcount() {
                    return reviewcount;
                }

                public void setReviewcount(int reviewcount) {
                    this.reviewcount = reviewcount;
                }
            }
        }

        public static class SpecialhotellistBean {
            /**
             * hotelcode : sample string 2
             * hotelname : sample string 3
             * hotelengname : sample string 4
             * regions : [{"regionid":"sample string 1","region":"sample string 2"},{"regionid":"sample string 1","region":"sample string 2"}]
             * star : 5.0
             * tripadrating : 6.1
             * tripadratingimgurl : sample string 7
             * tripadreviewcount : 8
             * triptypes : [{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3},{"hotelid":"sample string 1","triptype":"sample string 2","reviewcount":3}]
             * address : ["sample string 1","sample string 2"]
             * descript : sample string 9
             * longitude : sample string 10
             * latitude : sample string 11
             * paytotal : 12.0
             * currency : sample string 13
             * coverpic : sample string 14
             * orderby : 15
             * hotelfacilist : [{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"},{"iconname":"sample string 1","facilityid":"sample string 2","facility":"sample string 3","notes":"sample string 4","costinfo":"sample string 5"}]
             */

            private String hotelcode;
            private String hotelname;
            private String hotelengname;
            private double star;
            private double tripadrating;
            private String tripadratingimgurl;
            private int tripadreviewcount;
            private String descript;
            private String longitude;
            private String latitude;
            private double paytotal;
            private String currency;
            private String coverpic;
            private int orderby;
            private List<RegionsBeanXX> regions;
            private List<TriptypesBeanXXX> triptypes;
            private List<String> address;
            private List<HotelfacilistBeanXX> hotelfacilist;

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

            public double getStar() {
                return star;
            }

            public void setStar(double star) {
                this.star = star;
            }

            public double getTripadrating() {
                return tripadrating;
            }

            public void setTripadrating(double tripadrating) {
                this.tripadrating = tripadrating;
            }

            public String getTripadratingimgurl() {
                return tripadratingimgurl;
            }

            public void setTripadratingimgurl(String tripadratingimgurl) {
                this.tripadratingimgurl = tripadratingimgurl;
            }

            public int getTripadreviewcount() {
                return tripadreviewcount;
            }

            public void setTripadreviewcount(int tripadreviewcount) {
                this.tripadreviewcount = tripadreviewcount;
            }

            public String getDescript() {
                return descript;
            }

            public void setDescript(String descript) {
                this.descript = descript;
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

            public double getPaytotal() {
                return paytotal;
            }

            public void setPaytotal(double paytotal) {
                this.paytotal = paytotal;
            }

            public String getCurrency() {
                return currency;
            }

            public void setCurrency(String currency) {
                this.currency = currency;
            }

            public String getCoverpic() {
                return coverpic;
            }

            public void setCoverpic(String coverpic) {
                this.coverpic = coverpic;
            }

            public int getOrderby() {
                return orderby;
            }

            public void setOrderby(int orderby) {
                this.orderby = orderby;
            }

            public List<RegionsBeanXX> getRegions() {
                return regions;
            }

            public void setRegions(List<RegionsBeanXX> regions) {
                this.regions = regions;
            }

            public List<TriptypesBeanXXX> getTriptypes() {
                return triptypes;
            }

            public void setTriptypes(List<TriptypesBeanXXX> triptypes) {
                this.triptypes = triptypes;
            }

            public List<String> getAddress() {
                return address;
            }

            public void setAddress(List<String> address) {
                this.address = address;
            }

            public List<HotelfacilistBeanXX> getHotelfacilist() {
                return hotelfacilist;
            }

            public void setHotelfacilist(List<HotelfacilistBeanXX> hotelfacilist) {
                this.hotelfacilist = hotelfacilist;
            }

            public static class RegionsBeanXX {
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

            public static class TriptypesBeanXXX {
                /**
                 * hotelid : sample string 1
                 * triptype : sample string 2
                 * reviewcount : 3
                 */

                private String hotelid;
                private String triptype;
                private int reviewcount;

                public String getHotelid() {
                    return hotelid;
                }

                public void setHotelid(String hotelid) {
                    this.hotelid = hotelid;
                }

                public String getTriptype() {
                    return triptype;
                }

                public void setTriptype(String triptype) {
                    this.triptype = triptype;
                }

                public int getReviewcount() {
                    return reviewcount;
                }

                public void setReviewcount(int reviewcount) {
                    this.reviewcount = reviewcount;
                }
            }

            public static class HotelfacilistBeanXX {
                /**
                 * iconname : sample string 1
                 * facilityid : sample string 2
                 * facility : sample string 3
                 * notes : sample string 4
                 * costinfo : sample string 5
                 */

                private String iconname;
                private String facilityid;
                private String facility;
                private String notes;
                private String costinfo;

                public String getIconname() {
                    return iconname;
                }

                public void setIconname(String iconname) {
                    this.iconname = iconname;
                }

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
    }
}
