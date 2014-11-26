/*
 * Copyright (C) 2014 Dashman
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package srwmxstatictextureextractor;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Jonatan
 */
public class Main {

    static String bin_file = "STATIC2_ADD.BIN";
    static long start_of_files = 0x26a980;
    static long end_of_files = 0x2dd980;
    static RandomAccessFile f;
    static int tex_counter = 0;


    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here

        if (args.length == 1){
            if (args[0].equals("-i")){ // Insert files
                try{
                    insertFiles();
                    insertBattle();
                    insertFont();

                    return; // END
                }catch (IOException ex) {
                    System.err.println("ERROR: Couldn't read file.");   // END
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else{
                if (args[0].equals("-e")){  // Extract files
                    try{
                        extractFont();
                        extractBattle();
                        extractFiles();

                        return; // END
                    }catch (IOException ex) {
                        System.err.println("ERROR: Couldn't read file.");   // END
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
                else{
                    System.out.println("ERROR: Wrong number of parameters: " + args.length);
                    System.out.println("EXTRACT:\n java -jar tex_extract -e");
                    System.out.println("INSERT:\n java -jar tex_extract -i");
                    return;
                }
            }
        }
    }


    // Takes a 4-byte hex little endian and returns its int value
    public static int byteSeqToInt(byte[] byteSequence){
        if (byteSequence.length != 4)
            return -1;

        int value = 0;
        value += byteSequence[0] & 0xff;
        value += (byteSequence[1] & 0xff) << 8;
        value += (byteSequence[2] & 0xff) << 16;
        value += (byteSequence[3] & 0xff) << 24;
        return value;
    }


    // Extract TX48 files from STATIC2_ADD.BIN
    public static void extractFiles() throws IOException{
        RandomAccessFile file = new RandomAccessFile(bin_file, "r");

        byte[] header = new byte[64];

        boolean error = false;

        boolean bpp8;
        int width = 0;
        int height = 0;
        int size = 0;

        long offset = start_of_files;
        int counter = 0;

        byte[] CLUT = new byte[64];
        byte[] image;
        byte[] aux = new byte[4];

        int padding = 0;

        while (offset < end_of_files && !error){
            file.seek(offset);

            file.read(header);

            offset += 64;

            if (header[0] != 'T' || header[1] != 'X' || header[2] != '4' || header[3] != '8'){
                System.err.println("ERROR: Looking for TX48 file in the wrong place.\nSTATIC2_ADD.BIN might have been altered before.");
                System.err.println("Offset: " + offset);
                error = true;
            }

            if (!error){
                bpp8 = false;
                if (header[4] == 1)
                    bpp8 = true;

                aux[0] = header[8];
                aux[1] = header[9];
                aux[2] = header[10];
                aux[3] = header[11];

                width = byteSeqToInt(aux);

                aux[0] = header[12];
                aux[1] = header[13];
                aux[2] = header[14];
                aux[3] = header[15];

                height = byteSeqToInt(aux);

                aux[0] = header[28];
                aux[1] = header[29];
                aux[2] = header[30];
                aux[3] = header[31];

                size = byteSeqToInt(aux);

                if (bpp8)
                    CLUT = new byte[1024];
                else
                    CLUT = new byte[64];

                image = new byte[size];

                file.read(CLUT);

                offset += CLUT.length;

                // Colours in the CLUT are in the RGBA format. BMP uses BGRA format, so we need to swap Rs and Bs
                for (int i = 0; i < CLUT.length; i+= 4){
                    byte swap = CLUT[i];
                    CLUT[i] = CLUT[i+2];
                    CLUT[i+2] = swap;
                }

                file.read(image);

                offset += image.length;

                // The image has to be flipped to show it properly in BMP
                int dimX = width;
                if (!bpp8)
                    dimX = dimX / 2;

                byte[] pixels_R = image.clone();
                for (int i = 0, j = image.length - dimX; i < image.length; i+=dimX, j-=dimX){
                    for (int k = 0; k < dimX; ++k){
                        image[i + k] = pixels_R[j + k];
                    }
                }

                // If it's a 4bpp image, the nibbles have to be reversed for BMP
                if (!bpp8){
                    for (int i = 0; i < image.length; i++){
                        image[i] = (byte) ( ( (image[i] & 0x0f) << 4) | ( (image[i] & 0xf0) >> 4) );
                    }
                }

                byte depth = 4;

                if (bpp8)
                    depth = 8;

                writeBMP("TX48", CLUT, image, width, height, depth, counter);

                counter++;

                // Calculate padding. Files are 2048-byte alligned.
                int total_size = 64 + CLUT.length + image.length;
                int rest = total_size % 2048;
                padding = 0;

                if (rest != 0){
                    padding = 2048 - rest;
                    offset += padding;
                }
            }
        }

        file.close();
    }


    // Extract the image containing the battle display from STATIC2_ADD.BIN
    public static void extractBattle() throws IOException{
        RandomAccessFile file = new RandomAccessFile(bin_file, "r");

        int width = 512;
        int height = 512;

        long offset = 0xf4000 ;

        byte[] CLUT = new byte[1024];
        byte[] image = new byte[0x40000];

        file.seek(offset);

        file.read(CLUT);

        // Colours in the CLUT are in the RGBA format. BMP uses BGRA format, so we need to swap Rs and Bs
        for (int i = 0; i < CLUT.length; i+= 4){
            byte swap = CLUT[i];
            CLUT[i] = CLUT[i+2];
            CLUT[i+2] = swap;
        }

        file.read(image);


        // The image has to be flipped to show it properly in BMP
        byte[] pixels_R = image.clone();
        for (int i = 0, j = image.length - width; i < image.length; i+=width, j-=width){
            for (int k = 0; k < width; ++k){
                image[i + k] = pixels_R[j + k];
            }
        }

        writeBMP("Battle", CLUT, image, width, height, (byte) 8, 0);

        file.close();
    }


    // Extract the raw font as 256x144 images from STATIC2_ADD.BIN
    public static void extractFont() throws IOException{
        RandomAccessFile file = new RandomAccessFile(bin_file, "r");

        int width = 256;
        int height = 144;

        long offset = 0x40000 ;
        int size = 0x4800;

        byte[] CLUT = new byte[64];
        byte[] image = new byte[size];

        // The image has no CLUT. Prepare a custom CLUT for it
        /* The following CLUT is taken from the TX48 file with the numbers. Last colour is changed to avoid repetition.
         00 00 00 00
         0A 2B 05 FF
         2F 2D 41 FF
         58 4E 53 FF
         70 6B 6F FF
         B4 74 6B FF
         AC 85 7A FF
         A5 8D 8D FF
         B3 98 9A FF
         B0 A5 A7 FF
         CC BC BC FF
         E1 D7 D9 FF
         E3 E0 E7 FF
         F1 F0 F0 FF
         FF FF FF FF
         C0 C0 C0 FF */
        CLUT[0] = (byte) 0x00; 
        CLUT[1] = (byte) 0x00;
        CLUT[2] = (byte) 0x00;
        CLUT[3] = (byte) 0x00;

        CLUT[4] = (byte) 0x0A;
        CLUT[5] = (byte) 0x2B;
        CLUT[6] = (byte) 0x05;
        CLUT[7] = (byte) 0xFF;

        CLUT[8] = (byte) 0x2F;
        CLUT[9] = (byte) 0x2D;
        CLUT[10] = (byte) 0x41;
        CLUT[11] = (byte) 0xFF;

        CLUT[12] = (byte) 0x58;
        CLUT[13] = (byte) 0x4E;
        CLUT[14] = (byte) 0x53;
        CLUT[15] = (byte) 0xFF;

        CLUT[16] = (byte) 0x70;
        CLUT[17] = (byte) 0x6B;
        CLUT[18] = (byte) 0x6F;
        CLUT[19] = (byte) 0xFF;

        CLUT[20] = (byte) 0xB4;
        CLUT[21] = (byte) 0x74;
        CLUT[22] = (byte) 0x6B;
        CLUT[23] = (byte) 0xFF;

        CLUT[24] = (byte) 0xAC;
        CLUT[25] = (byte) 0x85;
        CLUT[26] = (byte) 0x7A;
        CLUT[27] = (byte) 0xFF;

        CLUT[28] = (byte) 0xA5;
        CLUT[29] = (byte) 0x8D;
        CLUT[30] = (byte) 0x8D;
        CLUT[31] = (byte) 0xFF;

        CLUT[32] = (byte) 0xB3;
        CLUT[33] = (byte) 0x98;
        CLUT[34] = (byte) 0x9A;
        CLUT[35] = (byte) 0xFF;

        CLUT[36] = (byte) 0xB0;
        CLUT[37] = (byte) 0xA5;
        CLUT[38] = (byte) 0xA7;
        CLUT[39] = (byte) 0xFF;

        CLUT[40] = (byte) 0xCC;
        CLUT[41] = (byte) 0xBC;
        CLUT[42] = (byte) 0xBC;
        CLUT[43] = (byte) 0xFF;

        CLUT[44] = (byte) 0xE1;
        CLUT[45] = (byte) 0xD7;
        CLUT[46] = (byte) 0xD9;
        CLUT[47] = (byte) 0xFF;

        CLUT[48] = (byte) 0xE3;
        CLUT[49] = (byte) 0xE0;
        CLUT[50] = (byte) 0xE7;
        CLUT[51] = (byte) 0xFF;

        CLUT[52] = (byte) 0xF1;
        CLUT[53] = (byte) 0xF0;
        CLUT[54] = (byte) 0xF0;
        CLUT[55] = (byte) 0xFF;

        CLUT[56] = (byte) 0xFF;
        CLUT[57] = (byte) 0xFF;
        CLUT[58] = (byte) 0xFF;
        CLUT[59] = (byte) 0xFF;
        
        CLUT[60] = (byte) 0xC0;
        CLUT[61] = (byte) 0xC0;
        CLUT[62] = (byte) 0xC0;
        CLUT[63] = (byte) 0xFF;

        file.seek(offset);

        for (int counter = 0; counter < 40; counter++){
            file.read(image);

            // The image has to be flipped to show it properly in BMP
            int dimX = width / 2;

            byte[] pixels_R = image.clone();
            for (int i = 0, j = image.length - dimX; i < image.length; i+=dimX, j-=dimX){
                for (int k = 0; k < dimX; ++k){
                    image[i + k] = pixels_R[j + k];
                }
            }

            // Swap the nibbles
            for (int i = 0; i < image.length; i++){
                image[i] = (byte) ( ( (image[i] & 0x0f) << 4) | ( (image[i] & 0xf0) >> 4) );
            }

            writeBMP("Font", CLUT, image, width, height, (byte) 4, counter);
        }

        file.close();
    }


    // Insert the TX48_xx.bmp files back into STATIC2_ADD.BIN
    public static void insertFiles() throws IOException{
        File directory = new File("."); // current folder

        RandomAccessFile f_bin = new RandomAccessFile(bin_file, "rw");
        long bin_offset = start_of_files;

        // Find the BMP files in the folder
        File[] listOfFiles = directory.listFiles(new FilenameFilter(){
            public boolean accept(File dir, String filename) {  // I *THINK* they're in alphanumeric order
                return ( filename.startsWith("TX48_") &&
                        ( filename.endsWith(".BMP") || filename.endsWith(".bmp") ) );
            }
        });

        // Abort if the number of files found is different than 80
        if (listOfFiles.length != 80){
            System.err.println("ERROR: Number of BMP files incorrect. There should be 80 TX48_xx.bmp files.");
            System.out.println("Found " + listOfFiles.length + " files.");
            f_bin.close();
            return;
        }
        else{
            for (int num = 0; num < 80; num++){
                RandomAccessFile bmp_file = new RandomAccessFile(listOfFiles[num].getAbsolutePath(), "r");

                byte[] header = new byte[54];
                byte[] aux = new byte[4];
                byte[] pixels = null;
                byte[] CLUT = null;

                int offset; // Start of image data
                int width = 0;
                //int height = 0;

                byte col_depth; // 04 for 4bpp, 08 for 8bpp

                bmp_file.read(header);

                aux[0] = header[10];
                aux[1] = header[11];
                aux[2] = header[12];
                aux[3] = header[13];
                offset = byteSeqToInt(aux);

                aux[0] = header[18];
                aux[1] = header[19];
                aux[2] = header[20];
                aux[3] = header[21];
                width = byteSeqToInt(aux);

                /*aux[0] = header[22];
                aux[1] = header[23];
                aux[2] = header[24];
                aux[3] = header[25];
                height = byteSeqToInt(aux);*/

                col_depth = header[28];

                // 1) Get the CLUT
                CLUT = new byte[64];
                if (col_depth == 8)
                    CLUT = new byte[1024];

                bmp_file.seek(offset - CLUT.length);

                bmp_file.read(CLUT);   // This places us at the beginning of the image data

                // Colours in the CLUT are in the RGBA format. BMP uses BGRA format, so we need to swap Rs and Bs
                for (int i = 0; i < CLUT.length; i+= 4){
                    byte swap = CLUT[i];
                    CLUT[i] = CLUT[i+2];
                    CLUT[i+2] = swap;
                }

                pixels = new byte[(int) listOfFiles[num].length() - offset];

                // 2) Grab the image data
                bmp_file.read(pixels);
                bmp_file.close();

                // 3) Turn it upside down
                byte[] pixels_R = pixels.clone();
                int dimX = width;
                if (col_depth != 8)
                    dimX = dimX / 2;

                for (int i = 0, j = pixels.length - dimX; i < pixels.length; i+=dimX, j-=dimX){
                    for (int k = 0; k < dimX; ++k){
                        pixels[i + k] = pixels_R[j + k];
                    }
                }

                // Turns out that if the image is stored as 4bpp, the nibbles have to be reversed
                if (col_depth == 4){
                    for (int i = 0; i < pixels.length; i++){
                        pixels[i] = (byte) ( ( (pixels[i] & 0x0f) << 4) | ( (pixels[i] & 0xf0) >> 4) );
                    }
                }

                // 4) Prepare the TX48 header
                // * This step might be unnecessary, since we're not allowing the images to grow as of now...
                byte[] tx48hed = new byte[64];

                tx48hed[0] = 'T';
                tx48hed[1] = 'X';
                tx48hed[2] = '4';
                tx48hed[3] = '8';

                if (col_depth == 8)
                    tx48hed[4] = 1;

                // Next 4 bytes: Width
                tx48hed[8] = (byte) header[18];
                tx48hed[9] = (byte) header[19];
                tx48hed[10] = (byte) header[20];
                tx48hed[11] = (byte) header[21];

                // Next 4 bytes: Height
                tx48hed[12] = (byte) header[22];
                tx48hed[13] = (byte) header[23];
                tx48hed[14] = (byte) header[24];
                tx48hed[15] = (byte) header[25];

                // Next 4 bytes: start of palette (always 64)
                tx48hed[16] = 64;

                // Next 4 bytes: size of palette (64 for 4bpp, 1024 for 8bpp)
                if (col_depth == 4)
                    tx48hed[20] = 64;
                else
                    tx48hed[21] = 4;    // 0x400 is 1024

                // Next 4 bytes: start of image data
                int start_image = 64 + CLUT.length;

                tx48hed[24] = (byte) (start_image & 0xff);
                tx48hed[25] = (byte) ( (start_image >> 8 ) & 0xff);
                tx48hed[26] = (byte) ( (start_image >> 16 ) & 0xff);
                tx48hed[27] = (byte) ( (start_image >> 24 ) & 0xff);

                // Next 4 bytes: size of image data
                tx48hed[28] = (byte) (pixels.length & 0xff);
                tx48hed[29] = (byte) ( (pixels.length >> 8 ) & 0xff);
                tx48hed[30] = (byte) ( (pixels.length >> 16 ) & 0xff);
                tx48hed[31] = (byte) ( (pixels.length >> 24 ) & 0xff);

                // 5) Overwrite everything
                f_bin.seek(bin_offset);

                f_bin.write(tx48hed);

                bin_offset += 64;

                f_bin.write(CLUT);

                bin_offset += CLUT.length;

                f_bin.write(pixels);

                bin_offset += pixels.length;

                // 6) Calculate padding. Files are 2048-byte alligned.
                int total_size = 64 + CLUT.length + pixels.length;
                int rest = total_size % 2048;
                int padding = 0;

                if (rest != 0){
                    padding = 2048 - rest;
                    bin_offset += padding;
                }

                System.out.println(listOfFiles[num].getName() + " inserted successfully.");
            }

            f_bin.close();
        }
    }


    // Insert Battle_00.bmp back into STATIC2_ADD.BIN
    public static void insertBattle() throws IOException{
        RandomAccessFile bmp_file = new RandomAccessFile("Battle_00.bmp", "r");
        
        byte[] CLUT = new byte[1024];
        byte[] pixels = new byte[0x40000];
        int width = 512;

        byte[] header = new byte[54];

        bmp_file.read(header);

        byte[] aux = new byte[4];

        aux[0] = header[10];
        aux[1] = header[11];
        aux[2] = header[12];
        aux[3] = header[13];
        int offset = byteSeqToInt(aux);

        bmp_file.seek(offset - 1024);

        bmp_file.read(CLUT);

        // Colours in the CLUT are in the RGBA format. BMP uses BGRA format, so we need to swap Rs and Bs
        for (int i = 0; i < CLUT.length; i+= 4){
            byte swap = CLUT[i];
            CLUT[i] = CLUT[i+2];
            CLUT[i+2] = swap;
        }

        bmp_file.read(pixels);

        // The image has to be flipped to show it properly in BMP
        byte[] pixels_R = pixels.clone();
        for (int i = 0, j = pixels.length - width; i < pixels.length; i+=width, j-=width){
            for (int k = 0; k < width; ++k){
                pixels[i + k] = pixels_R[j + k];
            }
        }

        bmp_file.close();

        RandomAccessFile f_bin = new RandomAccessFile(bin_file, "rw");
        long bin_offset = 0xf4000;

        f_bin.seek(bin_offset);

        f_bin.write(CLUT);
        f_bin.write(pixels);

        f_bin.close();

        System.out.println("Battle_00.bmp inserted successfully.");
    }


    // Insert the Font_xx.bmp files back into STATIC2_ADD.BIN
    public static void insertFont() throws IOException{
        File directory = new File("."); // current folder

        RandomAccessFile f_bin = new RandomAccessFile(bin_file, "rw");
        long bin_offset = 0x40000;

        // Find the BMP files in the folder
        File[] listOfFiles = directory.listFiles(new FilenameFilter(){
            public boolean accept(File dir, String filename) {  // I *THINK* they're in alphanumeric order
                return ( filename.startsWith("Font_") &&
                        ( filename.endsWith(".BMP") || filename.endsWith(".bmp") ) );
            }
        });

        // Abort if the number of files found is different than 80
        if (listOfFiles.length != 40){
            System.err.println("ERROR: Number of Font files incorrect. There should be 40 Font_xx.bmp files.");
            System.out.println("Found " + listOfFiles.length + " files.");
            f_bin.close();
            return;
        }
        else{
            for (int num = 0; num < 40; num++){
                RandomAccessFile bmp_file = new RandomAccessFile(listOfFiles[num].getAbsolutePath(), "r");

                byte[] header = new byte[54];
                byte[] aux = new byte[4];
                byte[] pixels = new byte[0x4800];

                int offset; // Start of image data
                int width = 256;
                //int height = 144;

                // 1) Get the offset of the image data
                bmp_file.read(header);

                aux[0] = header[10];
                aux[1] = header[11];
                aux[2] = header[12];
                aux[3] = header[13];
                offset = byteSeqToInt(aux);

                // 2) Grab the image data
                bmp_file.seek(offset);

                bmp_file.read(pixels);
                bmp_file.close();

                // 3) Turn it upside down
                byte[] pixels_R = pixels.clone();
                int dimX = width / 2;

                for (int i = 0, j = pixels.length - dimX; i < pixels.length; i+=dimX, j-=dimX){
                    for (int k = 0; k < dimX; ++k){
                        pixels[i + k] = pixels_R[j + k];
                    }
                }

                // The nibbles have to be reversed
                for (int i = 0; i < pixels.length; i++){
                    pixels[i] = (byte) ( ( (pixels[i] & 0x0f) << 4) | ( (pixels[i] & 0xf0) >> 4) );
                }

                // 4) Overwrite the raw image
                f_bin.seek(bin_offset);

                f_bin.write(pixels);

                bin_offset += pixels.length;

                System.out.println(listOfFiles[num].getName() + " inserted successfully.");
            }

            f_bin.close();
        }
    }


    // Outputs a BMP file with the given data
    public static void writeBMP(String filename, byte[] CLUT, byte[] imageData, int width, int height, byte depth, int number){
        byte[] header = new byte[54];

        // Prepare the header
        // * All sizes are little endian

        // Byte 0: '42' (B) Byte 1: '4d' (M)
        header[0] = 0x42;
        header[1] = 0x4d;

        // Next 4 bytes: file size (header + CLUT + pixels)
        int file_size = 54 + CLUT.length + imageData.length;
        header[2] = (byte) (file_size & 0xff);
        header[3] = (byte) ((file_size >> 8) & 0xff);
        header[4] = (byte) ((file_size >> 16) & 0xff);
        header[5] = (byte) ((file_size >> 24) & 0xff);

        // Next 4 bytes: all 0
        header[6] = 0;
        header[7] = 0;
        header[8] = 0;
        header[9] = 0;

        // Next 4 bytes: offset to start of image (header + CLUT)
        int offset = file_size - imageData.length;
        header[10] = (byte) (offset & 0xff);
        header[11] = (byte) ((offset >> 8) & 0xff);
        header[12] = (byte) ((offset >> 16) & 0xff);
        header[13] = (byte) ((offset >> 24) & 0xff);

        // Next 4 bytes: 28 00 00 00
        header[14] = 40;
        header[15] = 0;
        header[16] = 0;
        header[17] = 0;

        // Next 4 bytes: Width
        header[18] = (byte) (width & 0xff);
        header[19] = (byte) ((width >> 8) & 0xff);
        header[20] = (byte) ((width >> 16) & 0xff);
        header[21] = (byte) ((width >> 24) & 0xff);

        // Next 4 bytes: Height
        header[22] = (byte) (height & 0xff);
        header[23] = (byte) ((height >> 8) & 0xff);
        header[24] = (byte) ((height >> 16) & 0xff);
        header[25] = (byte) ((height >> 24) & 0xff);

        // Next 2 bytes: 01 00 (number of planes in the image)
        header[26] = 1;
        header[27] = 0;

        // Next 2 bytes: bits per pixel ( 04 00 or 08 00 )
        header[28] = depth;
        header[29] = 0;

        // Next 4 bytes: 00 00 00 00 (compression)
        header[30] = 0;
        header[31] = 0;
        header[32] = 0;
        header[33] = 0;

        // Next 4 bytes: image size in bytes (pixels)
        header[34] = (byte) (imageData.length & 0xff);
        header[35] = (byte) ((imageData.length >> 8) & 0xff);
        header[36] = (byte) ((imageData.length >> 16) & 0xff);
        header[37] = (byte) ((imageData.length >> 24) & 0xff);

        // Next 12 bytes: all 0 (horizontal and vertical resolution, number of colours)
        header[38] = 0;
        header[39] = 0;
        header[40] = 0;
        header[41] = 0;
        header[42] = 0;
        header[43] = 0;
        header[44] = 0;
        header[45] = 0;
        header[46] = 0;
        header[47] = 0;
        header[48] = 0;
        header[49] = 0;

        // Next 4 bytes: important colours (= number of colours)
        header[50] = 0;
        header[51] = (byte)(CLUT.length / 4);
        header[52] = 0;
        header[53] = 0;

        // Check if folder with the name of the bin_file exists. If not, create it.
        String path = bin_file + "_extract";
        File folder = new File(path);
        if (!folder.exists()){
            boolean success = folder.mkdir();
            if (!success){
                System.err.println("ERROR: Couldn't create folder.");
                return;
            }
        }

        // Create the bmp file inside said folder
        String file_path = filename + "_";

        if (number < 10)
            file_path += "0";
        
        file_path += number + ".bmp";
        path += "/" + file_path;
        try {
            RandomAccessFile bmp = new RandomAccessFile(path, "rw");

            bmp.write(header);
            bmp.write(CLUT);
            bmp.write(imageData);

            bmp.close();

            System.out.println(file_path + " saved successfully.");
            tex_counter++;
        } catch (IOException ex) {
            System.err.println("ERROR: Couldn't write " + file_path);
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
