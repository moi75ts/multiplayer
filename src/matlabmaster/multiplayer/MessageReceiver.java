package matlabmaster.multiplayer;

public interface MessageReceiver {
    void onMessageReceived(String message);
    boolean isActive();
}