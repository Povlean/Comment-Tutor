package com.hmdp;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@SpringBootTest
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {
    @Test
    public void test() {
        for(int i = 0;i < 100;i++) {
            System.out.println(i);
        }
    }

}
