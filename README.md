SRW MX Portable Static Texture Extractor by Dashman
-------------------------------------

This program allows you to extract and reinsert the textures inside STATIC2_ADD.BIN. You'll need to have Java installed in your computer to operate this.

How to extract:

1) Extract STATIC2_ADD.BIN to the folder with the program.
2) In a command / shell window (or using a batch / script file), execute this:

java -jar tex_extract.jar -e

3) The textures will be generated in a STATIC2_ADD.BIN_extracted subfolder.

How to insert:

1) Put the program, the BIN file and all the BMP files in the same directory.
2) Execute

java -jar tex_extract.jar -i

3) The extracted files will be re-inserted into STATIC2_ADD.BIN.


IMPORTANT NOTES:

* Don't change the names of files. The program looks for STATIC2_ADD.BIN, Font_*.bmp files, TX48_*.bmp files and Battle_00.bmp. If those files are named differently, they'll be ignored.

* All extrated textures are indexed, and should stay indexed when you re-insert them. You can change the palettes for all files except the Font files to your liking and will be shown like that ingame, but don't increase their palettes, or the size of most files will double.

* It's still not clear how these textures are referenced by the game (I haven't found a pointer table for these), so changing their dimensions is strictly forbidden. However, seeing how there's a big room for padding in most cases, there's actually some room for expansion. I'd say it is safe to turn the 32x32 and 64x32 images into 112x32 to accomodate more text. I'd recommend you test this (because otherwise some messages won't fit after translation).

* This program works with hardcoded offsets. That is, everything is expected to be where it originally is. In the future, if STATIC2_ADD.BIN is altered and its contents are expanded / reduced / moved, this tool will most probably stop working properly.

* Last but not least, good luck.