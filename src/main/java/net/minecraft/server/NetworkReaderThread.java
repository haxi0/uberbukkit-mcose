package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

class NetworkReaderThread extends Thread {
    private boolean fast; // Poseidon
    final NetworkManager a;

    NetworkReaderThread(NetworkManager networkmanager, String s) {
        super(s);
        this.a = networkmanager;
        this.fast = PoseidonConfig.getInstance().getBoolean("settings.faster-packets.enabled", true); // Poseidon
    }

    public void run() {
        synchronized (NetworkManager.a) {
            ++NetworkManager.b;
        }

        try {
            while (NetworkManager.a(this.a)) {
                if (NetworkManager.b(this.a)) {
                    break;
                }

                while (NetworkManager.c(this.a)) {
                    ;
                }

                try {
                    sleep(this.fast ? 2L : 100L);
                } catch (InterruptedException interruptedexception) {
                    ;
                }
            }
        } finally {
            synchronized (NetworkManager.a) {
                --NetworkManager.b;
            }
        }
    }
}
