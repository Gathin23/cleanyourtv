package com.gathin.homedashboard;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class MetricTest {
    private JSONObject state(String value,String unit)throws Exception{return new JSONObject().put("state",value).put("attributes",new JSONObject().put("unit_of_measurement",unit));}
    private SensorCard card(String mode,int decimals)throws Exception{return new SensorCard(new JSONObject().put("entity","sensor.example").put("mode",mode).put("decimals",decimals),0);}
    private Metric parse(String value,String unit,boolean percent)throws Exception{return Metric.parse(state(value,unit),card(percent?"percentage":"power",percent?0:2));}
    @Test public void batteryPercent()throws Exception{assertEquals("66 %",parse("66","%",true).text);}
    @Test public void wattsBecomeKilowatts()throws Exception{assertEquals("2.40 kW",parse("2400","W",false).text);}
    @Test public void zeroSolarIsAValidReading()throws Exception{assertEquals("0.00 kW",parse("0","W",false).text);}
    @Test public void kilowattsStayKilowatts()throws Exception{assertEquals("1.25 kW",parse("1.25","kW",false).text);}
    @Test public void dailyEnergyIsNotInstantaneousPower()throws Exception{assertEquals("Check power unit",parse("12","kWh",false).text);}
    @Test public void unknownIsNotZero()throws Exception{assertEquals("Unavailable",parse("unavailable","W",false).text);assertTrue(Double.isNaN(parse("unknown","%",true).value));}
    @Test public void invalidBatteryRangeIsRejected()throws Exception{assertEquals("Check % sensor",parse("120","%",true).text);}
    @Test public void nanIsNotAReading()throws Exception{assertEquals("Unavailable",parse("NaN","W",false).text);}
    @Test public void autoKeepsTemperatureUnit()throws Exception{assertEquals("22.4 °C",Metric.parse(state("22.36","°C"),card("auto",1)).text);}
    @Test public void autoDoesNotConvertEnergyToPower()throws Exception{assertEquals("12.4 kWh",Metric.parse(state("12.40","kWh"),card("auto",2)).text);}
    @Test public void autoDoesNotConvertWatts()throws Exception{assertEquals("2400 W",Metric.parse(state("2400","W"),card("auto",2)).text);}
    @Test public void autoHandlesUnitlessAndTextStates()throws Exception{assertEquals("3",Metric.parse(state("3",""),card("auto",2)).text);assertEquals("home",Metric.parse(state("home",""),card("auto",2)).text);assertEquals("Off",Metric.parse(state("off",""),card("auto",2)).text);}
    @Test public void autoUnavailableHasNoMisleadingUnit()throws Exception{assertEquals("Unavailable",Metric.parse(state("unknown","°C"),card("auto",2)).text);}
    @Test public void numericModesRejectText()throws Exception{assertTrue(Double.isNaN(Metric.parse(state("on","%"),card("percentage",0)).value));}
    @Test public void percentageRejectsWrongUnit()throws Exception{assertEquals("Check % sensor",Metric.parse(state("50","°C"),card("percentage",0)).text);}
    @Test public void percentageAndPowerRespectPrecision()throws Exception{assertEquals("46.2 %",Metric.parse(state("46.21","%"),card("percentage",1)).text);assertEquals("2.400 kW",Metric.parse(state("2400","W"),card("power",3)).text);}
    @Test public void staleReportTimeIsRetained()throws Exception{JSONObject o=state("unavailable","% Х").put("last_updated","2026-11-12T12:00:00Z");assertEquals("2026-11-12T12:00:00Z",Metric.parse(o,card("auto",2)).reported);}
    @Test public void nonFiniteNumbersAreUnavailable()throws Exception{assertEquals("Unavailable",Metric.parse(state("1e309","W"),card("auto",2)).text);assertEquals("Unavailable",Metric.parse(state("Infinity",""),card("auto",2)).text);}
    @Test public void largeNumericStatesStayCompact()throws Exception{assertTrue(Metric.parse(state("1e30","Wh"),card("auto",2)).text.length()<25);}
}
