package com.example.mycommute.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class KmaWeatherGridTest {
    @Test public void convertsSeoulCityHallToOfficialGrid() {
        int[] p=KmaWeatherClient.toGrid(37.5664,126.9778);
        assertEquals(60,p[0]);
        assertEquals(127,p[1]);
    }
}
