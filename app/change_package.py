import os
import sys
import shutil

#TODO: add NDK support

initial_folder = os.path.abspath(".")
package_separator = "."

#arguments
old_package = sys.argv[1]
new_package = sys.argv[2]
proguard = None

def charge_proguard():
	global proguard
	if(len(sys.argv) > 3):
		proguard = sys.argv[3]	

def show_arguments():
	print("old package: " + old_package)
	print("new package: " + new_package)
	print("proguard: " + str(proguard))

def check_original_route():
	print("checking original package...")
	original_route = initial_folder + os.sep + "src" + os.sep + "main" + os.sep + "java" + os.sep + old_package.replace(package_separator, os.sep)
	if(os.path.isdir(original_route)):
		print("original folder exists")
	else:
		print("original folder not found, write a correct original package")
		sys.exit()

def replace_text(path_file, old_text, new_text):
	f = open(path_file, "r")
	file_text = f.read()
	f.close()
	t = file_text.replace(old_text, new_text)
	f = open(path_file, "w")
	f.write(t)
	f.close()

def change_files(path_folder):
	print("current directory: " + path_folder)
	
	for f in os.listdir(path_folder):
		print("file " + str(f))
		#is a folder
		if os.path.isdir(path_folder + os.sep + f):
			abs_path = os.path.abspath(path_folder + os.sep + str(f))
			#ignore build folder
			if(str(f) != "build"):
				change_files(abs_path)
		#is a file
		else:
			#only change java, xml and gradle files
			if(str(f).endswith(".java") or str(f).endswith(".xml") or str(f).endswith(".gradle")):
				replace_text(path_folder + os.sep + str(f), old_package, new_package)
			elif(proguard != None):
				if(str(f) == proguard):
					replace_text(path_folder + os.sep + str(f), old_package, new_package)
					print("proguard changed")
			else:
				print("ignore this file")

def move_folders():
	original_route = initial_folder + os.sep + "src" + os.sep + "main" + os.sep + "java" + os.sep + old_package.replace(package_separator, os.sep)
	destiny_route = initial_folder + os.sep + "src" + os.sep + "main" + os.sep + "java" + os.sep + new_package.replace(package_separator, os.sep)
	shutil.move(original_route, initial_folder + os.sep + "my_temporal_folder")
	shutil.rmtree(initial_folder + os.sep + "src" + os.sep + "main" + os.sep + "java" + os.sep)
	print("new java route: " + destiny_route)
	shutil.move(initial_folder + os.sep + "my_temporal_folder", destiny_route)

def init_script():
	charge_proguard()
	show_arguments()
	check_original_route()
	change_files(initial_folder)
	move_folders()
	print("finished success")

init_script()