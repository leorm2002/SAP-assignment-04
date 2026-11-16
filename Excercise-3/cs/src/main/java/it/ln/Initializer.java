package it.ln;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

class Initializer {
    public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {

        try (var cs = new CriticalSection();) {
            cs.init();

            try (var permit = cs.waitToken()) {

            }
        }

    }
}
