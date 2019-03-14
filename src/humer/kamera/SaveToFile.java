/*
 * Copyright 2019 peter.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package humer.kamera;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;



import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import humer.kamera.Kam;
import java.awt.Component;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import javax.swing.table.DefaultTableModel;


/**
 *
 * @author peter
 */
public class SaveToFile {
    
    public int sbus;
    public int sdevice;
    public byte sendpunktadresse;
    public String sdevicePath;
    
    public int sALT_SETTING;
    public int scamFormatIndex ;   // MJPEG // YUV // bFormatIndex: 1 = uncompressed
    public int scamFrameIndex ; // bFrameIndex: 1 = 640 x 360;       2 = 176 x 144;     3 =    320 x 240;      4 = 352 x 288;     5 = 640 x 480;
    public int simageWidth;
    public int simageHeight;
    public int scamFrameInterval ; // 333333 YUV = 30 fps // 666666 YUV = 15 fps
    public int spacketsPerRequest ;
    public int smaxPacketSize ;
    public int sactiveUrbs ;
    public String svideoformat;
    private static String saveFilePath = "save/saveFile.sav";
    
    private Kam kam;
    public JLabel video;
    
    private boolean abfrage = true;
    
    static ArrayList<String> paths = new ArrayList<>(50);
    private static ArrayList<String> saveValues = new ArrayList<>(20);
    
    private int [] arrayFormatFrameIndexes;
    private int [] convertedMaxPacketSize;
    private int [] numberFormatIndexes;
    private String[] frameDescriptorsArray;
    private String [] dwFrameIntervalArray;
    private String [] convertedMaxPacketSizeArray;
    private String [] videoFormatArray;
    private PhraseUvcDescriptor phrasedUvcDescriptor;
    private PhraseUvcDescriptor.FormatIndex formatIndex;
    private PhraseUvcDescriptor.FormatIndex.FrameIndex frameIndex;
    

    public SaveToFile (){
        initKamClass();
    }
    
    
    
    public void initKamClass(){
        if (kam == null) kam = new Kam(1);
    }
            
        
    
    private void fetchTheValues(){
        
        sbus = kam.BUS;
        sdevice = kam.DEVICE;
        sendpunktadresse = kam.ENDPOINT_ADDRESS;
        sdevicePath = kam.DEVICE_PATH;
        sALT_SETTING = kam.ALT_SETTING;
        svideoformat = kam.videoformat;
        scamFormatIndex = kam.CAM_FORMAT_INDEX;
        simageWidth = kam.imageWidth;
        simageHeight = kam.imageHeight;
        scamFrameIndex = kam.CAM_FRAME_INDEX;
        scamFrameInterval = kam.CAM_FRAME_INTERVAL;
        spacketsPerRequest = kam.PACKETS_PER_REQUEST;
        smaxPacketSize = kam.MAX_PACKET_SIZE;
        sactiveUrbs = kam.ACTIVE_URBS;
    }
    
    private void writeTheValues(){
        
        kam.BUS = sbus;
        kam.DEVICE = sdevice;
        kam.ENDPOINT_ADDRESS = sendpunktadresse;
        kam.DEVICE_PATH = sdevicePath;
        kam.ALT_SETTING = sALT_SETTING;
        kam.CAM_FORMAT_INDEX = scamFormatIndex;
        kam.imageWidth = simageWidth;
        kam.imageHeight = simageHeight;
        kam.CAM_FRAME_INDEX = scamFrameIndex;
        kam.CAM_FRAME_INTERVAL = scamFrameInterval;
        kam.PACKETS_PER_REQUEST = spacketsPerRequest;
        kam.MAX_PACKET_SIZE = smaxPacketSize;
        kam.ACTIVE_URBS = sactiveUrbs;
        
    }
    
    
    
    public void startRestore() {
        //initKamClass();
        int option;
        String name;
        paths = new ArrayList<>(50);
        StringBuilder stringBuilder = new StringBuilder();
        /*
        Object[] options = {"Use the standard path", "Select new filepath"};
        option = JOptionPane.showOptionDialog(null, "Would you like to use the standard filepath?","Filepath ...", JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE,null,options , options[0]);
        if (option != JOptionPane.OK_OPTION){
            name = JOptionPane.showInputDialog("Please type in the Path:   (Example:    /home/user/camera/  )");
            rootPath = name;
        }
        */
        File s = new File(saveFilePath).getAbsoluteFile();
                s.getParentFile().mkdirs();
                String filePath = s.getParent();
                filePath += "/";
                
                
        recursiveFind(Paths.get(filePath), System.out::println);
        //recursiveFind(Paths.get(rootPath), p -> {if (p.toFile().getName().toString().equals("src")) { System.out.println(p); }});
        for (int i = 0; i < paths.size(); i++) {
            stringBuilder.append(String.format("%d   ->   ", (i+1)));
            stringBuilder.append(paths.get(i));
            stringBuilder.append("\n");
        }
        name = JOptionPane.showInputDialog(String.format("Please type the name of the restore file.\nFollowing files are stored in the directory:\n \n%s\n\n   To select the first file type in 1, or for the secound file 2\n   Or type in a name (without the Directory) (for example: camera)" , stringBuilder.toString() ));
        if (name == null) JOptionPane.showMessageDialog(null, "save canceled","Save Canceled", JOptionPane.INFORMATION_MESSAGE) ;
        else if (name.isEmpty() == false) {
            if (isInteger(name) == true) { 
                try { restorFromFile(paths.get((Integer.parseInt(name) - 1))); }
                catch (Exception e) { Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, e); JOptionPane.showMessageDialog(null, "Error restoring the file","Error while restoring the file", JOptionPane.ERROR_MESSAGE);}   
            } else {restorFromFile((filePath += name += ".sav"));      JOptionPane.showMessageDialog(null, "save complete","Save Complete", JOptionPane.INFORMATION_MESSAGE);}
        }
        
        System.out.println("Restore completed");
        System.out.println("restore bus = " + sbus);
        System.out.println("restore camFrameInterval  =  " + scamFrameInterval);
        System.out.println("ALT_SETTING = " + sALT_SETTING);
        System.out.println("camFormatIndex = " + scamFormatIndex);
        System.out.println("camFrameIndex = " + scamFrameIndex);
        System.out.println("camFrameInterval = " + scamFrameInterval);
        System.out.println("imageWidth = " + simageWidth);
        System.out.println("imageHeight = " + simageHeight);
        System.out.println("devpath = " + sdevicePath);
        System.out.println("videoformat = " + svideoformat);
    }
    
    
    
    public void startEditSave() {
        initKamClass();
        fetchTheValues();
        
        
        JTextField ALT_SETTING0 = new JTextField();
        JTextField maxPacketSize0 = new JTextField();
        JTextField camFormatIndex0 = new JTextField();
        JTextField videoformat0 = new JTextField();
        JTextField camFrameIndex0 = new JTextField();
        JTextField imageWidth0 = new JTextField();
        JTextField imageHeight0 = new JTextField();
        JTextField camFrameInterval0 = new JTextField();
        JTextField packetsPerRequest0 = new JTextField();
        JTextField activeUrbs0 = new JTextField();
        
        Object[] message = {
            "ALT_SETTING:             Typ in the Camera ALT_SETTING: --> normaly from 0 to 10 ..." + String.format("\n    Stored Value: ALT_SETTING = %d", sALT_SETTING), ALT_SETTING0,
            "MaxPacketSize:           Typ in the Camera MaxPacketSize: --> normaly from 1 to 2 ..."+ String.format("\n     Stored Value: maxPacketSize = %d", smaxPacketSize) , maxPacketSize0, 
            "CamFormatIndex:          Typ in the Camera CamFormatIndex: --> normaly from 1 to 5 ... (This represents the Resolution)"+ String.format("\n     Stored Value: camFormatIndex = %d", scamFormatIndex) , camFormatIndex0,
            "MJPEG = 0   // YUV = 1:  Typ in the Camera Frameformat: MJPEG = 0 YUV = 1 (means uncompressed) Note: Uncompressed are at least 10 different formats.\n      So the picture could be displayed in a wrong color way ... This format is for YUY2" + String.format("\n     Stored Value: VideoSetting = %s", svideoformat), videoformat0,
            "CamFrameIndex:           Typ in the Camera camFrameIndex: --> normaly from 1 to 5 ...(This represents the Resolution)" + String.format("\n     Stored Value: camFrameIndex = %d", scamFrameIndex), camFrameIndex0,
            "ImageWidth:              Typ in the Camera imageWidth: --> Some formats are 640x480 or 1920x1240 ...(Only type in the Width --> 640 or 1920 .." + String.format("\n      Stored Value: imageWidth = %d", simageWidth), imageWidth0,
            "ImageHeight:             Typ in the Camera imageHeight: --> (Only type in the Width --> 480 or 1240 .." + String.format("\n     Stored Value: imageHeight = %d", simageHeight), imageHeight0,
            "CamFrameInterval         Typ in the Camera camFrameInterval:  333333 --> means 30 fps (Frames per secound)\n              666666 ---> means 15 fps 1000000 = 10 fps    2000000 = 5 fps:" + String.format("\n     Stored Value: camFrameInterval = %d", scamFrameInterval), camFrameInterval0,
            "PacketsPerRequest:       Typ in the Camera packetsPerRequest:  (at least 1 packet up to 8 or 32 or 64 or 128 or ..." + String.format("\n     Stored Value: packetsPerRequest = %d", spacketsPerRequest), packetsPerRequest0,
            "ActiveUrbs:              Typ in the Camera activeUrbs: At least 1 active URB (USB REQUEST BLOCK) up to 8, or 16, 64, or ..." + String.format("\n     Stored Value: activeUrbs = %d", sactiveUrbs), activeUrbs0,
        };
        // password.getText().equals("h")
        int option = JOptionPane.showConfirmDialog(null, message, "Edit the Camera Values   --->  You can leave Fields blank. When you enter no value, the stored value will be kept.", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            if (ALT_SETTING0.getText().isEmpty() == false)  sALT_SETTING = Integer.parseInt(ALT_SETTING0.getText());
            if (maxPacketSize0.getText().isEmpty() == false)  smaxPacketSize = Integer.parseInt(maxPacketSize0.getText());
            if (camFormatIndex0.getText().isEmpty() == false)  scamFormatIndex = Integer.parseInt(camFormatIndex0.getText());
            if (videoformat0.getText().isEmpty() == false){ 
                if (Integer.parseInt(videoformat0.getText()) == 0) svideoformat = "mjpeg";
                else if (Integer.parseInt(videoformat0.getText()) == 1) svideoformat = "yuy2";
                else svideoformat = "unknown";}
            if (camFrameIndex0.getText().isEmpty() == false)  scamFrameIndex = Integer.parseInt(camFrameIndex0.getText());
            if (imageWidth0.getText().isEmpty() == false)  simageWidth = Integer.parseInt(imageWidth0.getText());
            if (imageHeight0.getText().isEmpty() == false)  simageHeight = Integer.parseInt(imageHeight0.getText());
            if (camFrameInterval0.getText().isEmpty() == false)  scamFrameInterval = Integer.parseInt(camFrameInterval0.getText());
            if (packetsPerRequest0.getText().isEmpty() == false)  spacketsPerRequest = Integer.parseInt(packetsPerRequest0.getText());
            if (activeUrbs0.getText().isEmpty() == false)  sactiveUrbs = Integer.parseInt(activeUrbs0.getText());
            System.out.println("Input saved");
            //writeTheValues();
            
            Object[] options = {"Save to a File", "Don't Save !"};
            option = JOptionPane.showOptionDialog(null, "Would you like to save the settings to a file?" ,"Save the Settings ?", JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE,null,options , options[0]);
            if (option == JOptionPane.OK_OPTION) {
                String name;
                /*
                Object[] options2 = {"Use the standard path", "Select new filepath"};
                option = JOptionPane.showOptionDialog(null, String.format("Would you like to use the standard filepath?\nThe Filepath is:   %s" , rootPath ),"Filepath ...", JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE,null,options2 , options2[0]);
                if (option != JOptionPane.OK_OPTION){
                    name = JOptionPane.showInputDialog("Please type in the Path:   (Example:    /home/user/camera/  )");
                    rootPath = name;
                }
                */
                paths = new ArrayList<>(50);
                
                File s = new File(saveFilePath).getAbsoluteFile();
                s.getParentFile().mkdirs();
                String filePath = s.getParent();
                filePath += "/";
                
                recursiveFind(Paths.get(s.getParent()), System.out::println);
                //recursiveFind(Paths.get(rootPath), p -> {if (p.toFile().getName().toString().equals("src")) { System.out.println(p); }});
                System.out.println("Anzahl der Dateien: " + paths.size() + "\n");
                for (int i = 0; i < paths.size(); i++) {
                    System.out.println( paths.get(i) );
                }
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0; i < paths.size(); i++) {
                    stringBuilder.append(String.format("%d   ->   ", (i+1)));
                    stringBuilder.append(paths.get(i));
                    stringBuilder.append("\n");
                }
                s = null;
                
                name = JOptionPane.showInputDialog(String.format("Please type the name of the savefile.\n   Following Files were stored in the directory:\n \n%s\n\n To select the First File Type in 1, or for the secound File 2\nOr Type in a name (without the Directory) (for example: camera)" , stringBuilder.toString() ));
                if (name == null) JOptionPane.showMessageDialog(null, "save canceld","Save canceld", JOptionPane.INFORMATION_MESSAGE);
                else if (name.isEmpty() == false) {
                    if (isInteger(name) == true) { 
                        try { saveValueToFile(paths.get((Integer.parseInt(name)) - 1)); }
                        catch (Exception e) { Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, e); JOptionPane.showMessageDialog(null, "Error saving the file","Error while saving the file", JOptionPane.ERROR_MESSAGE);}   
                    } else {saveValueToFile(filePath  += name += ".sav");      }
                } 
            }
        } else  System.out.println("Input canceled");
    } 
    
    private void saveValueToFile (String savePath) {
        
        System.out.println("savePath = " + savePath);
        try {  // Catch errors in I/O if necessary.
            /*
        File dump = new File(DUMP_FILE).getAbsoluteFile();
        dump.getParentFile().mkdirs();
        */
        
        File file = new File(savePath).getAbsoluteFile();
        file.getParentFile().mkdirs();
        if (file.exists())  file.delete();
        
        FileOutputStream saveFile=new FileOutputStream(savePath);

            ObjectOutputStream save = new ObjectOutputStream(saveFile);

            save.writeObject(sbus);
            save.writeObject(sdevice);
            save.writeObject(sendpunktadresse);
            save.writeObject(sdevicePath);
            save.writeObject(sALT_SETTING);
            save.writeObject(svideoformat);
            save.writeObject(scamFormatIndex);
            save.writeObject(scamFrameIndex);
            save.writeObject(simageWidth);
            save.writeObject(simageHeight);
            save.writeObject(scamFrameInterval);
            save.writeObject(spacketsPerRequest);
            save.writeObject(smaxPacketSize);
            save.writeObject(sactiveUrbs);
            save.writeObject(saveFilePath);
            
            // Close the file.
            save.close(); // This also closes saveFile.
        } catch (Exception e) { Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, e);}
        
        JOptionPane.showMessageDialog(null, "save complete","Save Complete", JOptionPane.INFORMATION_MESSAGE);
    }
            
    
    public void  recursiveFind(Path path, Consumer<Path> c) {
        try (DirectoryStream<Path> newDirectoryStream = Files.newDirectoryStream(path)) {
            StreamSupport.stream(newDirectoryStream.spliterator(), false).peek(p -> {
                c.accept(p);
                paths.add(p.toString());
                if (p.toFile()
                        .isDirectory()) {
                     recursiveFind(p, c);
                }
            }).collect(Collectors.toList());
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static boolean isInteger(String s) {
        return isInteger(s,10);
    }

    public static boolean isInteger(String s, int radix) {
        if(s.isEmpty()) return false;
        for(int i = 0; i < s.length(); i++) {
            if(i == 0 && s.charAt(i) == '-') {
                if(s.length() == 1) return false;
                else continue;
            }
            if(Character.digit(s.charAt(i),radix) < 0) return false;
        }
        return true;
    }
    
    public void restorFromFile(String pathToFile){
        try{
            FileInputStream saveFile = new FileInputStream(pathToFile);
            ObjectInputStream save = new ObjectInputStream(saveFile);
            sbus = (Integer) save.readObject();
            sdevice = (Integer) save.readObject();
            sendpunktadresse = (Byte) save.readObject();
            sdevicePath = (String) save.readObject();
            sALT_SETTING = (Integer) save.readObject();
            svideoformat = (String) save.readObject();
            scamFormatIndex  = (Integer) save.readObject();  
            scamFrameIndex  = (Integer) save.readObject();
            simageWidth = (Integer) save.readObject();
            simageHeight = (Integer) save.readObject();
            scamFrameInterval  = (Integer) save.readObject();
            spacketsPerRequest  = (Integer) save.readObject();
            smaxPacketSize  = (Integer) save.readObject();
            sactiveUrbs  = (Integer) save.readObject();
            saveFilePath  = (String) save.readObject();
            save.close(); 
        }
        catch(Exception exc){
            exc.printStackTrace(); 
        }
    }
    
    
    
    
    public void setUpWithUvcValues(CameraSearch cs) {
        
        phrasedUvcDescriptor = cs.getPhrasedUvcDescriptor();
        arrayFormatFrameIndexes = new int [phrasedUvcDescriptor.formatIndex.size()];
        for (int i=0; i<phrasedUvcDescriptor.formatIndex.size(); i++) {
            formatIndex = phrasedUvcDescriptor.getFormatIndex(i);
            arrayFormatFrameIndexes[i] = formatIndex.frameIndex.size();
        }
        int [] maxPacketSize = cs.maxPacketSizeArray;
        convertedMaxPacketSize = new int [maxPacketSize.length];
        for (int a=1; a<convertedMaxPacketSize.length; a++) {
            convertedMaxPacketSize [a] = returnConvertedValue(maxPacketSize[a]);
        }
        
        
        convertedMaxPacketSizeArray = new String [convertedMaxPacketSize.length-1];
        for (int a =1; a<convertedMaxPacketSize.length; a++) {
            convertedMaxPacketSizeArray[a-1] = Integer.toString(convertedMaxPacketSize[a]);
        }
        String input = (String) JOptionPane.showInputDialog(null, "Select the Max Packet Size (Important for Mediathekdevices)", "Select the Max Packet Size", JOptionPane.QUESTION_MESSAGE, null,  convertedMaxPacketSizeArray, convertedMaxPacketSizeArray[convertedMaxPacketSizeArray.length-1]);
        if (input != null) {
            for (int i=1; i<convertedMaxPacketSize.length; i++) {
                if (input.matches(convertedMaxPacketSizeArray[i-1]) ) {
                    sALT_SETTING = i;
                }
            }
            smaxPacketSize = Integer.parseInt(input.toString());
            System.out.println("sALT_SETTING = " + sALT_SETTING);
            System.out.println("smaxPacketSize = " + smaxPacketSize);
        }
        
        JTextField packetsPerRequest0 = new JTextField();
        JTextField activeUrbs0 = new JTextField();
        
        Object[] message = {
            "PacketsPerRequest:       \nTyp in the Camera packetsPerRequest:   (at least 1 packet up to 8 or 32 or 64 or 128 or ...)" + String.format("\n     Stored Value: packetsPerRequest = %d", spacketsPerRequest), packetsPerRequest0,
            "\n\nActiveURBs:              \nTyp in the Camera activeUrbs: At least 1 active URB (USB REQUEST BLOCK) up to 8, or 16, 64, or ..." + String.format("\n     Stored Value: activeUrbs = %d", sactiveUrbs), activeUrbs0,
            "ActiveURBs means how many Blocks of Packages should run paralell to each other\nThis represents an IsochronousTransfer"
        };
        // password.getText().equals("h")
        int option = JOptionPane.showConfirmDialog(null, message, String.format("Next you have to select how many Packets with the size of  - %d -   you want to send:" , smaxPacketSize), JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            if (packetsPerRequest0.getText().isEmpty() == false)  spacketsPerRequest = Integer.parseInt(packetsPerRequest0.getText());
            if (activeUrbs0.getText().isEmpty() == false)  sactiveUrbs = Integer.parseInt(activeUrbs0.getText());
            System.out.println("Input saved");
        } 
        numberFormatIndexes = new int [phrasedUvcDescriptor.formatIndex.size()];
        videoFormatArray = new String [phrasedUvcDescriptor.formatIndex.size()];
        for (int a =0; a<phrasedUvcDescriptor.formatIndex.size(); a++) {
            formatIndex = phrasedUvcDescriptor.getFormatIndex(a);
            System.out.println("formatIndex.videoformat = " + formatIndex.videoformat);
            if (formatIndex.videoformat == PhraseUvcDescriptor.FormatIndex.Videoformat.yuy2) System.out.println("PhraseUvcDescriptor.FormatIndex.Videoformat.yuy2 = " + PhraseUvcDescriptor.FormatIndex.Videoformat.yuy2);
            else System.out.println(" ............");
            
            numberFormatIndexes[a] = formatIndex.formatIndexNumber;
            System.out.println("numberFormatIndexes[a] = " + numberFormatIndexes[a]);
            videoFormatArray[a] = formatIndex.videoformat.toString();
        } 
        input = (String) JOptionPane.showInputDialog(null, "             Select the FormatIndex              ", "This Videoformats are supported of your camera", JOptionPane.QUESTION_MESSAGE, null,  videoFormatArray, videoFormatArray[videoFormatArray.length-1]);
        if (input != null) {
            svideoformat = (input.toString());
            for (int i=0; i<videoFormatArray.length; i++) {
                if (input.matches(videoFormatArray[i]) ) {
                    scamFormatIndex = numberFormatIndexes[i];
                    formatIndex = phrasedUvcDescriptor.getFormatIndex(i);
                    frameDescriptorsArray = new String [formatIndex.numberOfFrameDescriptors];
                    String inp;
                    for (int j=0; j<formatIndex.numberOfFrameDescriptors; j++) {
                        frameIndex = formatIndex.getFrameIndex(j);
                        StringBuilder stringb = new StringBuilder();
                        stringb.append(Integer.toString(frameIndex.wWidth));
                        stringb.append(" x ");
                        stringb.append(Integer.toString(frameIndex.wHeight));
                        frameDescriptorsArray[j] = stringb.toString();
                    }
                    inp = (String) JOptionPane.showInputDialog(null, "Select the camera Resolution", "Following Resolutions are supported:", JOptionPane.QUESTION_MESSAGE, null,  frameDescriptorsArray, frameDescriptorsArray[frameDescriptorsArray.length-1]);
                    if (inp != null) {
                        for (int j=0; j<formatIndex.numberOfFrameDescriptors; j++) {
                            if (inp.equals(frameDescriptorsArray[j])) {
                                frameIndex = formatIndex.getFrameIndex(j);
                                
                                scamFrameIndex = frameIndex.frameIndex;
                                System.out.println("scamFrameIndex = " + scamFrameIndex);
                                simageWidth = frameIndex.wWidth;
                                simageHeight = frameIndex.wHeight;
                                
                                dwFrameIntervalArray = new String [frameIndex.dwFrameInterval.length];
                                for (int k=0; k<dwFrameIntervalArray.length; k++) {
                                    dwFrameIntervalArray[k] = Integer.toString(frameIndex.dwFrameInterval[k]);
                                }
                                String textInput = (String) JOptionPane.showInputDialog(null, String.format("Example: 333333 = 30 fps (Frames per Secound);   666666 = 15 fps\nA higher value means less Pictures per Secound\nOne Frame is one Picture."), "Select the FrameIntervall", JOptionPane.QUESTION_MESSAGE, null,  dwFrameIntervalArray, dwFrameIntervalArray[dwFrameIntervalArray.length-1]);
                                if (textInput != null) {
                                    scamFrameInterval = Integer.parseInt(textInput);
                                    System.out.println("scamFrameInterval = " + scamFrameInterval);
                                }
                            } 
                        }
                    }
                    
                    
                    
                    
                    
                    
                }
            }
            
        }
        /*
        
    */
    }
    
    private int returnConvertedValue(int wSize){
        String st = Integer.toBinaryString(wSize);
        StringBuilder result = new StringBuilder();
        result.append(st);
        //System.out.println("Integer.parseInt(result.toString(), 2) = " + Integer.parseInt(result.toString()));
        if (result.length()<12) return Integer.parseInt(result.toString(), 2);
        else if (result.length() == 12) {
            String a = result.substring(0, 1);
            String b = result.substring(1, 12);
            int c = Integer.parseInt(a, 2);
            int d = Integer.parseInt(b, 2);
            return (c+1)*d;
        } else {
            String a = result.substring(0, 2);
            String b = result.substring(2,13);
            int c = Integer.parseInt(a, 2);
            int d = Integer.parseInt(b, 2);
            return (c+1)*d;
        }
    }
    
    public void saveValueToFileUvcDescriptor () {
        
        fetchTheValues();
        
        Object[] options = {"Save to a File", "Don't Save !"};
        int option = JOptionPane.showOptionDialog(null, "Would you like to save the settings to a file?" ,"Save the Settings ?", JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE,null,options , options[0]);
        if (option == JOptionPane.OK_OPTION) {
            String name;
            /*
                Object[] options2 = {"Use the standard path", "Select new filepath"};
                option = JOptionPane.showOptionDialog(null, String.format("Would you like to use the standard filepath?\nThe Filepath is:   %s" , rootPath ),"Filepath ...", JOptionPane.YES_NO_OPTION,JOptionPane.QUESTION_MESSAGE,null,options2 , options2[0]);
                if (option != JOptionPane.OK_OPTION){
                    name = JOptionPane.showInputDialog("Please type in the Path:   (Example:    /home/user/camera/  )");
                    rootPath = name;
                }
                */ 
            paths = new ArrayList<>(50);
                
            File s = new File(saveFilePath).getAbsoluteFile();
            s.getParentFile().mkdirs();
            String filePath = s.getParent();
            filePath += "/";
                
            recursiveFind(Paths.get(s.getParent()), System.out::println);
            //recursiveFind(Paths.get(rootPath), p -> {if (p.toFile().getName().toString().equals("src")) { System.out.println(p); }});
            System.out.println("Anzahl der Dateien: " + paths.size() + "\n");
            for (int i = 0; i < paths.size(); i++) {
                System.out.println( paths.get(i) );
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < paths.size(); i++) {
                stringBuilder.append(String.format("%d   ->   ", (i+1)));
                stringBuilder.append(paths.get(i));
                stringBuilder.append("\n");
            }
            s = null;
               
            name = JOptionPane.showInputDialog(String.format("Please type the name of the savefile.\n   Following Files were stored in the directory:\n \n%s\n\n To select the First File Type in 1, or for the secound File 2\nOr Type in a name (without the Directory) (for example: camera)" , stringBuilder.toString() ));
            if (name == null) JOptionPane.showMessageDialog(null, "save canceld","Save canceld", JOptionPane.INFORMATION_MESSAGE);
            else if (name.isEmpty() == false) {
                if (isInteger(name) == true) { 
                    try { saveValueToFile(paths.get((Integer.parseInt(name)) - 1)); }
                    catch (Exception e) { Logger.getLogger(Kam.class.getName()).log(Level.SEVERE, null, e); JOptionPane.showMessageDialog(null, "Error saving the file","Error while saving the file", JOptionPane.ERROR_MESSAGE);}   
                } else {saveValueToFile(filePath  += name += ".sav");      }
            } 
            
        }
            
            
        
    }
    
    public void startUvcEditSave() {
        
 
       
        JComboBox<String> convertedMaxPacketSizeBox = new JComboBox<>(convertedMaxPacketSizeArray);
        JComboBox<String> frameDescriptorsBox = new JComboBox<>(frameDescriptorsArray);
        JComboBox<String> dwFrameIntervalBox = new JComboBox<>(dwFrameIntervalArray);
        
        
        convertedMaxPacketSizeBox.setSelectedIndex(-1);
        frameDescriptorsBox.setSelectedIndex(-1);
        dwFrameIntervalBox.setSelectedIndex(-1);
        
        
        JTextField packetsPerRequest0 = new JTextField();
        JTextField activeUrbs0 = new JTextField();
        
        
        JPanel panel = new JPanel(new GridLayout(0, 2));
        panel.add(new Label(String.format("MaxPacketSize = %d", smaxPacketSize)));
        panel.add(convertedMaxPacketSizeBox);
        panel.add(new Label(String.format("Video Format = %dx%d" , simageWidth, simageHeight)));
        panel.add(frameDescriptorsBox);
        panel.add(new Label(String.format("FrameInterval = %d", scamFrameInterval)));
        panel.add(dwFrameIntervalBox);
        panel.add(new Label(String.format("PacketsPerRequest = %d", spacketsPerRequest)));
        panel.add(packetsPerRequest0);
        panel.add(new Label(String.format("ActiveUrbs = %d", sactiveUrbs)));
        panel.add(activeUrbs0);
        
        
        
        
        
        int option = JOptionPane.showConfirmDialog(null, panel, String.format("Make your camera settings:" , smaxPacketSize), JOptionPane.OK_CANCEL_OPTION);
        
        if (option == JOptionPane.OK_OPTION) {
            
            if (packetsPerRequest0.getText().isEmpty() == false)  {
                spacketsPerRequest = Integer.parseInt(packetsPerRequest0.getText());
                System.out.println("spacketsPerRequest = " + spacketsPerRequest);
            }
            if (activeUrbs0.getText().isEmpty() == false)  {
                sactiveUrbs = Integer.parseInt(activeUrbs0.getText());
                System.out.println("sactiveUrbs = " + sactiveUrbs);
            }
            
            
            
            if (convertedMaxPacketSizeBox.getSelectedItem() != null) {
                String newConvertedMaxPacketSize = String.valueOf(convertedMaxPacketSizeBox.getSelectedItem());
                for (int i=1; i<convertedMaxPacketSize.length; i++) {
                    if (newConvertedMaxPacketSize.matches(convertedMaxPacketSizeArray[i-1]) ) {
                        sALT_SETTING = i;
                    }
                }
            
                smaxPacketSize = Integer.parseInt(newConvertedMaxPacketSize);
                System.out.println("sALT_SETTING = " + sALT_SETTING);
                System.out.println("smaxPacketSize = " + smaxPacketSize);
            }
            
            if (dwFrameIntervalBox.getSelectedItem() != null) {
                scamFrameInterval = Integer.parseInt(String.valueOf(dwFrameIntervalBox.getSelectedItem()));
                System.out.println("scamFrameInterval = " + scamFrameInterval);
            }
            
            
            
            
            
        } 
        
    }
        
        
    
}
     