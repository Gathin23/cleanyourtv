package io.github.luyzfernando08.sleeptimer;
import java.time.*; import java.util.*;
public class ScheduleCheck {
 static ZoneId z=ZoneId.of("Europe/Berlin");
 static long t(String v){return LocalDateTime.parse(v).atZone(z).toInstant().toEpochMilli();}
 static void check(String now, String expected, List<ScheduleRules.Rule> r,List<ScheduleRules.Pause> p,boolean wake){
  ScheduleRules.Event e=ScheduleRules.next(t(now),z,r,p);
  if(e==null||e.at!=t(expected)||e.wake!=wake)throw new AssertionError(now+" -> "+expected);
 }
 public static void main(String[] args){
  List<ScheduleRules.Rule> r=Arrays.asList(new ScheduleRules.Rule(31,480,true),new ScheduleRules.Rule(127,1380,false));
  List<ScheduleRules.Pause> p=Arrays.asList(new ScheduleRules.Pause(LocalDate.parse("2026-12-24"),LocalDate.parse("2027-01-10")));
  check("2026-11-02T07:00","2026-11-02T08:00",r,p,true);
  check("2026-11-02T08:00","2026-11-02T23:00",r,p,false);
  check("2026-12-24T07:00","2026-12-24T23:00",r,p,false);
  check("2027-01-10T23:30","2027-01-11T08:00",r,p,true);
  List<ScheduleRules.Rule> wake=Collections.singletonList(r.get(0));
  check("2026-12-24T07:00","2027-01-11T08:00",wake,p,true);
  List<ScheduleRules.Pause> overlapping=Arrays.asList(p.get(0),new ScheduleRules.Pause(LocalDate.parse("2027-01-01"),LocalDate.parse("2027-01-20")));
  check("2026-12-24T07:00","2027-01-21T08:00",wake,overlapping,true);
  check("2026-11-06T09:00","2026-11-09T08:00",wake,Collections.emptyList(),true);
  check("2026-03-29T00:00","2026-03-29T03:30",Collections.singletonList(new ScheduleRules.Rule(64,150,true)),Collections.emptyList(),true);
  check("2026-11-02T07:00","2026-11-02T08:00",Arrays.asList(new ScheduleRules.Rule(1,480,true),new ScheduleRules.Rule(1,480,false)),Collections.emptyList(),false);
  if(ScheduleRules.next(t("2026-11-02T07:00"),z,Collections.emptyList(),p)!=null)throw new AssertionError();
  System.out.println("10 schedule checks passed");
 }
}
