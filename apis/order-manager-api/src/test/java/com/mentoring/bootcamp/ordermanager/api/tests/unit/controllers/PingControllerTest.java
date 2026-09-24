package com.mentoring.bootcamp.ordermanager.api.tests.unit.controllers;

import com.mentoring.bootcamp.ordermanager.api.controllers.PingController;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingControllerTest {

    @Test
    void should_answer_pong() {
        assertThat(new PingController().ping()).isEqualTo("pong");
    }
}
