package com.gathin.homedashboard;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MetricTest {
    private JSONObject state(String value,String unit)throws Exception{return new JSONObject().put("state",value).put("attributes",new JSONObject().put("unit_of_measurement",unit));}
    @Test public void batteryPercent()throws Exception{assertEquals("66 %",Metric.parse(state("66","%"),true).text);}
    @Test public void wattsBecomeKilowatts()throws Exception{assertEquals("2.40 kW",Metric.parse(state("2400","W"),false).text);}
    @Test public void zeroSolarIsAValidReading()throws Exception{assertEquals("0.00 kW",Metric.parse(state("0","W"),false).text);}
    @Test public void kilowattsStayKilowatts()throws Exception{assertEquals("1.25 kW",Metric.parse(state("1.25","kW"),false).text);}
    @Test public void dailyEnergyIsNotInstantaneousPower()throws Exception{assertEquals("Check power unit",Metric.parse(state("12","kWh"),false).text);}
    @Test public void unknownIsNotZero()throws Exception{assertEquals("Unavailable",Metric.parse(state("unavailable","W"),false).text);assertTrue(Double.isNaN(Metric.parse(state("unknown","%"),true).value));}
    @Test public void invalidBatteryRangeIsRejected()throws Exception{assertEquals("Check SOC sensor",Metric.parse(state("120","%"),true).text);}
    @Test public void nanIsNotAReading()throws Exception{assertEquals("Unavailable",Metric.parse(state("NaN","W"),false).text);}
}
