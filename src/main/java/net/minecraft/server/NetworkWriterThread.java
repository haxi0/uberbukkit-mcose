package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.io.IOException;

class NetworkWriterThread extends Thread {
    private boolean fast; // Poseidon
    final NetworkManager a;

    NetworkWriterThread(NetworkManager networkmanager, String s) {
        super(s);
        this.a = networkmanager;
        this.fast = PoseidonConfig.getInstance().getBoolean("settings.faster-packets.enabled", true); // Poseidon
    }

    public void run() {
        synchronized (NetworkManager.a) {
            ++NetworkManager.c;
        }

        try {
            while (NetworkManager.a(this.a)) {
                boolean sentAnyPackets = false;
                while (NetworkManager.d(this.a)) {
                    sentAnyPackets = true;
                }

                if (sentAnyPackets) {
                    try {
                        if (NetworkManager.e(this.a) != null) {
                            NetworkManager.e(this.a).flush();
                        }
                    } catch (IOException ioexception) {
                        if (!NetworkManager.f(this.a)) {
                            NetworkManager.a(this.a, (Exception) ioexception);
                        }
                    }
                }

                try {
                    sleep(this.fast ? 2L : 100L);
                } catch (InterruptedException interruptedexception) {
                    ;
                }
            }
        } finally {
            synchronized (NetworkManager.a) {
                --NetworkManager.c;
            }
        }
    }
}
