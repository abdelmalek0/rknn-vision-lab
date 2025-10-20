package com.smartprints.rknn_vision_lab;

import android.app.Application;

import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.ConsolePrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy;
import com.elvishew.xlog.printer.file.clean.FileLastModifiedCleanStrategy;
import com.elvishew.xlog.printer.file.naming.DateFileNameGenerator;

public class App extends Application {

    private static final long MAX_TIME = 259_200_000; // 3 days

    public App(){
        System.out.println("App constructor");
        LogConfiguration config = new LogConfiguration.Builder()
                .logLevel(LogLevel.ALL)
                .tag("Logger")                                         // Specify TAG, default: "X-LOG"
//                .enableThreadInfo()                                    // Enable thread info, disabled by default
                .enableStackTrace(2)
                .build();

        Printer androidPrinter = new AndroidPrinter(true);         // Printer that print the log using android.util.Log
//        Printer consolePrinter = new ConsolePrinter();             // Printer that print the log to console using System.out
        Printer filePrinter = new FilePrinter                      // Printer that print(save) the log to file
                .Builder("/storage/emulated/0/Android/data/com.smartprints.rknn_vision_lab/files/logs")                         // Specify the directory path of log file(s)
                .fileNameGenerator(new DateFileNameGenerator())        // Default: ChangelessFileNameGenerator("log")
                .backupStrategy(new NeverBackupStrategy())             // Default: FileSizeBackupStrategy(1024 * 1024)
                .cleanStrategy(new FileLastModifiedCleanStrategy(MAX_TIME))     // Default: NeverCleanStrategy()
                .build();

        XLog.init(                                                 // Initialize XLog
                config,                                                // Specify the log configuration, if not specified, will use new LogConfiguration.Builder().build()
                androidPrinter,                                        // Specify printers, if no printer is specified, AndroidPrinter(for Android)/ConsolePrinter(for java) will be used.
                filePrinter);
        XLog.d("XLog initialized");
    }
}
