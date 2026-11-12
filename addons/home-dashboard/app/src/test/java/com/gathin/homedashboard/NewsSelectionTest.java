package com.gathin.homedashboard;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class NewsSelectionTest {
    private static Headline h(String title, long time) { return new Headline("source",title,"",time,""); }
    private static List<String> titles(List<Headline> stories){return stories.stream().map(x->x.title).collect(java.util.stream.Collectors.toList());}
    @Test public void recentStoriesAreOrderedAcrossSources() {
        List<Headline> result=NewsSelection.recent(List.of(h("old",1),h("third",2),h("first",4),h("second",3)),5);
        assertEquals(List.of("first","second","third","old"),titles(result));
    }
    @Test public void duplicateTitleKeepsNewerStory() {
        List<Headline> result=NewsSelection.recent(List.of(h("Title",1),h(" title ",2),h("other",3)),4);
        assertEquals(2,result.size());assertEquals(2,result.get(1).time);
    }
    @Test public void oldStoriesAreDropped() {
        assertTrue(NewsSelection.recent(List.of(h("old",1)),49*3600000L).isEmpty());
    }
    @Test public void UndatedStoriesFollowDatedStories() {
        List<Headline> result=NewsSelection.recent(List.of(h("unknown",0),h("dated",2)),3);
        assertEquals("dated",result.get(0).title);assertEquals("unknown",result.get(1).title);
    }
    @Test public void emptyTitlesAreIgnored() {
        assertTrue(NewsSelection.recent(List.of(h("  ",2)),3).isEmpty());
    }
    @Test public void carouselKeepsSixtyStoriesAtMost(){
        java.util.ArrayList<Headline> input=new java.util.ArrayList<>();for(int i=0;i<100;i++)input.add(h("story "+i,i+1));
        List<Headline> result=NewsSelection.recent(input,101);assertEquals(60,result.size());assertEquals("story 99",result.get(0).title);
    }
    @Test public void nextStoryEntersAtTopAndPushesOthersDown(){
        NewsSelection.Rotation r=new NewsSelection.Rotation(List.of(h("1",6),h("2",5),h("3",4),h("4",3),h("5",2),h("6",1)));
        assertEquals(List.of("1","2","3"),titles(r.visible()));
        assertTrue(r.advance());assertEquals(List.of("4","1","2"),titles(r.visible()));
        assertTrue(r.advance());assertEquals(List.of("5","4","1"),titles(r.visible()));
        assertTrue(r.advance());assertEquals(List.of("6","5","4"),titles(r.visible()));
    }
    @Test public void fourStoriesLoopWithoutDuplicates(){
        NewsSelection.Rotation r=new NewsSelection.Rotation(List.of(h("1",4),h("2",3),h("3",2),h("4",1)));
        for(int i=0;i<12;i++){assertTrue(r.advance());assertEquals(3,new java.util.HashSet<>(titles(r.visible())).size());}
        assertEquals(List.of("1","2","3"),titles(r.visible()));
    }
    @Test public void threeOrFewerStoriesStayVisible(){
        NewsSelection.Rotation r=new NewsSelection.Rotation(List.of(h("1",2),h("2",1)));
        assertFalse(r.advance());assertEquals(List.of("1","2"),titles(r.visible()));
        assertFalse(new NewsSelection.Rotation(List.of()).advance());
    }
    @Test public void sourceStaysAttachedToItsRotatingTitle(){
        Headline incoming=new Headline("Publisher Four","4","",1,"");
        NewsSelection.Rotation r=new NewsSelection.Rotation(List.of(h("1",4),h("2",3),h("3",2),incoming));
        r.advance();assertEquals("4",r.visible().get(0).title);assertEquals("Publisher Four",r.visible().get(0).source);
    }
}
