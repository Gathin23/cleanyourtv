package io.github.vitorcamillo.wolfgames;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class MoonlightCatalogTest {
    private static final String UUID = "00000000-0000-4000-8000-000000000001";
    private MoonlightCatalog.Game game(String n, String id, String image) { return new MoonlightCatalog.Game(n,id,image); }
    @Test public void allowsNamesForMoonlightCachedLookupAndCopiesInput() {
        List<MoonlightCatalog.Game> games = new ArrayList<>();
        games.add(game("Mario Kart 8 Deluxe",null,"mario-kart.png"));
        MoonlightCatalog c = new MoonlightCatalog("Wolf",UUID.toUpperCase(),games);
        games.clear(); assertEquals(1,c.games.size()); assertEquals(UUID,c.hostUuid); assertNull(c.games.get(0).appId);
        assertThrows(UnsupportedOperationException.class,()->c.games.clear());
    }
    @Test public void rejectsMalformedHostAndTitles() {
        for(String bad:new String[]{null,"","1-1-1-1-1",UUID+"x","../host"}) {
            assertThrows(IllegalArgumentException.class,()->new MoonlightCatalog("Wolf",bad,Collections.singletonList(game("Game",null,null))));
        }
        for(String bad:new String[]{null,""," "," Game","Game ","x\ny","x\u0000y",String.join("",Collections.nCopies(121,"x"))}) {
            assertThrows(IllegalArgumentException.class,()->game(bad,null,null));
        }
        assertEquals(120,game(String.join("",Collections.nCopies(120,"x")),null,null).name.length());
    }
    @Test public void idsHaveStrictIntegerBounds() {
        for(String bad:new String[]{"0","-1","01","1.0","2147483648","999999999999","1;cmd"," 1"}) {
            assertThrows(IllegalArgumentException.class,()->game("Game",bad,null));
        }
        assertEquals("2147483647",game("Game","2147483647",null).appId);
        assertEquals("1",game("Game","1",null).appId);
    }
    @Test public void blocksPathTraversalUrlsAndExecutableCovers() {
        for(String bad:new String[]{"../x.png","/x.png","x/y.png","x\\y.png","https://x.png","x.svg","x.png.exe",".png","x.PNG"}) {
            assertThrows(IllegalArgumentException.class,()->game("Game",null,bad));
        }
        assertEquals("game-1_cover.jpeg",game("Game",null,"game-1_cover.jpeg").image);
    }
    @Test public void enforcesCountAndUniqueness() {
        assertThrows(IllegalArgumentException.class,()->new MoonlightCatalog("Wolf",UUID,null));
        assertThrows(IllegalArgumentException.class,()->new MoonlightCatalog("Wolf",UUID,Collections.emptyList()));
        List<MoonlightCatalog.Game> list=new ArrayList<>();
        for(int i=0;i<64;i++)list.add(game("Game "+i,""+(i+1),null));
        assertEquals(64,new MoonlightCatalog("Wolf",UUID,list).games.size());
        list.add(game("overflow",null,null));
        assertThrows(IllegalArgumentException.class,()->new MoonlightCatalog("Wolf",UUID,list));
        assertThrows(IllegalArgumentException.class,()->new MoonlightCatalog("Wolf",UUID,java.util.Arrays.asList(game("A","1",null),game("B","1",null))));
        assertThrows(IllegalArgumentException.class,()->new MoonlightCatalog("Wolf",UUID,java.util.Arrays.asList(game("A",null,null),game("A",null,null))));
        assertThrows(IllegalArgumentException.class,()->new MoonlightCatalog("Wolf",UUID,java.util.Arrays.asList((MoonlightCatalog.Game)null)));
    }
    private List<MoonlightCatalog.Game> threeGames() {
        List<MoonlightCatalog.Game> games = new ArrayList<>();
        games.add(game("Alpha", "1", null));
        games.add(game("Beta", "2", null));
        games.add(game("Gamma", "3", null));
        return games;
    }
    @Test public void displayOrderKeepsCatalogOrderWithoutSavedNamesOrActive() {
        List<MoonlightCatalog.Game> games = threeGames();
        assertEquals(java.util.Arrays.asList(0,1,2),MoonlightCatalog.displayOrder(games,null,null));
        assertEquals(java.util.Arrays.asList(0,1,2),MoonlightCatalog.displayOrder(games,Collections.emptyList(),null));
        games.set(0,game("Alpha",null,null));
        assertEquals(java.util.Arrays.asList(0,1,2),MoonlightCatalog.displayOrder(games,null,"1"));
    }
    @Test public void displayOrderLeadsWithRunningGame() {
        List<MoonlightCatalog.Game> games = threeGames();
        assertEquals(java.util.Arrays.asList(1,2,0),MoonlightCatalog.displayOrder(games,java.util.Arrays.asList("Gamma","Alpha"),"2"));
        assertEquals(java.util.Arrays.asList(2,0,1),MoonlightCatalog.displayOrder(games,java.util.Arrays.asList("Gamma","Alpha"),"3"));
    }
    @Test public void displayOrderFollowsPartialSavedOrderAndIgnoresUnknownNames() {
        List<MoonlightCatalog.Game> games = threeGames();
        assertEquals(java.util.Arrays.asList(2,0,1),MoonlightCatalog.displayOrder(games,java.util.Arrays.asList("Gamma","Missing"),null));
        assertEquals(java.util.Arrays.asList(2,0,1),MoonlightCatalog.displayOrder(games,java.util.Arrays.asList("Missing","Gamma","Missing","Gamma"),null));
    }
    @Test public void displayOrderIgnoresActiveOutsideCatalogAndKeepsEveryIndexOnce() {
        List<MoonlightCatalog.Game> games = threeGames();
        List<Integer> order = MoonlightCatalog.displayOrder(games,java.util.Arrays.asList("Beta"),"999");
        assertEquals(java.util.Arrays.asList(1,0,2),order);
        assertEquals(3,order.size());
        assertEquals(3,new java.util.HashSet<>(order).size());
        for(int i=0;i<3;i++)assertTrue(order.contains(i));
    }
    @Test public void movedSwapsNeighbouringNamesAndLeavesTheInputAlone() {
        List<String> names = java.util.Arrays.asList("Alpha","Beta","Gamma");
        assertEquals(java.util.Arrays.asList("Beta","Alpha","Gamma"),MoonlightCatalog.moved(names,0,1));
        assertEquals(java.util.Arrays.asList("Alpha","Gamma","Beta"),MoonlightCatalog.moved(names,1,2));
        assertEquals(java.util.Arrays.asList("Alpha","Beta","Gamma"),names);
    }
    @Test public void movedRejectsPositionsOutsideTheList() {
        List<String> names = java.util.Arrays.asList("Alpha","Beta","Gamma");
        for(int[] bad:new int[][]{{-1,0},{0,-1},{0,3},{3,0},{2,3}}) {
            assertThrows(IllegalArgumentException.class,()->MoonlightCatalog.moved(names,bad[0],bad[1]));
        }
        assertThrows(IllegalArgumentException.class,()->MoonlightCatalog.moved(null,0,0));
        assertThrows(IllegalArgumentException.class,()->MoonlightCatalog.moved(Collections.emptyList(),0,0));
    }
    @Test public void refusesSymlinksOutsideCatalog() throws Exception {
        File directory=Files.createTempDirectory("moonlight-test").toFile();
        File outside=Files.createTempFile("cover", ".png").toFile();
        try {
            Files.createSymbolicLink(new File(directory,"escape.png").toPath(),outside.toPath());
            assertThrows(java.io.IOException.class,()->MoonlightCatalog.localFile(directory,"escape.png"));
            assertThrows(java.io.IOException.class,()->MoonlightCatalog.localFile(directory,"../escape.png"));
            assertEquals(directory,MoonlightCatalog.localFile(directory,"valid.png").getParentFile());
            assertThrows(java.io.IOException.class,()->MoonlightCatalog.localFile(directory,"script.svg"));
            assertThrows(java.io.IOException.class,()->MoonlightCatalog.localFile(directory,null));
            assertEquals("catalog.json",MoonlightCatalog.localFile(directory,"catalog.json").getName());
        } finally { Files.deleteIfExists(new File(directory,"escape.png").toPath()); outside.delete(); directory.delete(); }
    }
}
