package ch.poliscore;

import software.amazon.awscdk.App;

public class PoliscoreApp {
    public static void main(final String[] args) {
        App app = new App();

        new PoliscoreStack(app, "poliscore");

        app.synth();
    }
}
