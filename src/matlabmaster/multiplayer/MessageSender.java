package matlabmaster.multiplayer;

public interface MessageSender {
    void sendMessage(String message);
    boolean isActive();
}