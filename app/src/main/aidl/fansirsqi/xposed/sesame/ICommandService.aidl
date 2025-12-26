package fansirsqi.xposed.sesame;

import fansirsqi.xposed.sesame.ICallback;

interface ICommandService {
    void executeCommand(String command, ICallback callback);
}
