#!/bin/python3

from shutil import copy, copytree, rmtree, make_archive, move
from os.path import isfile, isdir
import os
from sys import argv
from pathlib import Path
import re

VAR_REGEX = "[A-Za-z_][A-Za-z0-9_]*"

def count_spaces(x: str) -> int:
    nr = 0
    for char in x:
        if char == " ":
            nr += 1
        elif char == "\t":
            nr += 4
        else:
            break
    return nr

def read_file_content(path: Path) -> str:
    with open(path, 'r') as file:
        return file.read()

def write_file_content(content: str, path: Path):
    with open(path, 'w') as file:
        file.write(content)

def resolve_imports(path: Path) -> [str]:
    content = read_file_content(path)

    lines = content.split('\n')
    lines_aux = []
    # We copy each line, resolving imports recursively
    for line in lines:
        if line.strip()[:8] == "#include":
            import_file = Path("shaders" + line.strip()[10:-1]) # TODO: handle relative paths
            import_content = resolve_imports(import_file)
            for import_line in import_content:
                lines_aux.append(import_line)
        else:
            lines_aux.append(line)

    return lines_aux

def extract_var_name(x: str):
    return re.findall(VAR_REGEX, x)[0]


def process_macros(content: [str]) -> [str]:
    function_macros = {}
    FN_REGEX = f"#define {VAR_REGEX}\\("

    content_aux = []

    for line in content:
        if re.search(FN_REGEX, line) != None:
            words = line.strip().split(' ')
            header = words[1].split('(')
            body = ' '.join(words[2:])

            name = header[0] # left half
            args = header[1][:-1].split(',') # Right half, without the closing bracket

            if name not in function_macros:
                function_macros[name] = {}
            function_macros[name]["args"] = args
            function_macros[name]["body"] = body
        else:
            line_aux = line
            for key in function_macros.keys():
                while line_aux.find(key) != -1:
                    st = line_aux.find(key)
                    header_end = line_aux.find('(', st)+1
                    
                    # Find end of function
                    bracket_cnt = 1
                    i = header_end
                    while bracket_cnt != 0:
                        if line_aux[i] == '(':
                            bracket_cnt += 1
                        elif line_aux[i] == ')':
                            bracket_cnt -= 1
                        i += 1
                    end = i-1

                    args = line_aux[header_end:end] # TODO: Make this handle multiple arguments
                    fn_arg = function_macros[key]["args"][0]

                    fn_body = function_macros[key]["body"]
                    fn_body = re.sub(rf"\b{fn_arg}\b", args, fn_body)

                    line_aux = line_aux[:st] + fn_body + line_aux[end+1:]
            content_aux.append(line_aux)

    return content_aux

class Symbol:
    name: str # Actual name that is referenced in the shader (eg. "foo")
    content: [str] # The full declaration (eg. "uniform vec4 foo;\n")
    index: int # Used for sorting at the end
    def __init__(self, name):
        self.name = name
        self.content = []
        self.isUsed = False
        self.index = 0
        self.isFunction = False

def split_to_symbols(content: [str]) -> {}:
    i = 0
    index = 0
    symbols = {}
    dontoptimize = False
    
    while i < len(content):
        if content[i] == '' or content[i] == '\n':
            i += 1
            continue
        cnt = content[i].strip()
        words = cnt.split(" ")

        if cnt.startswith(("flat", "noperspective")): # Two word qualifiers
            s = Symbol(extract_var_name(words[3]))
        # elif content[i].startswith("#define") and content[i].find("//") != -1: # Setting macros
        #     s = Symbol(words[1])
        elif cnt.startswith(("void", "bool", "float", "int", "vec2", "vec3", "vec4", "mat2", "mat3", "mat4", "mat2x3")):
            if content[i].find('(') != -1 and content[i].find(";") == -1: # Function
                s = Symbol(words[1].split("(")[0])
                indentation = count_spaces(content[i])
                while not (content[i].strip().startswith('}') and count_spaces(content[i]) == indentation): # Indentation level breaks everythng
                    s.content.append(content[i])
                    i += 1
                s.isFunction = True
            else: # Global variable
                s = Symbol(extract_var_name(words[1]))
        elif cnt.startswith(("uniform", "in", "out", "const", "attribute")): # One word qualifiers that aren't configurations
            s = Symbol(extract_var_name(words[2]))
            while content[i].find(';') == -1:
                s.content.append(content[i])
                i += 1
        elif cnt.startswith("// DONTOPTIMIZE-START"):
            dontoptimize = True
            i += 1
            continue
        elif cnt.startswith("// DONTOPTIMIZE-END"):
            dontoptimize = False
            i += 1
            continue
        elif cnt.startswith("//") and cnt.find("#define") == -1: # Comments, to be marked as used if they are directly above a function
            s = Symbol(index)
        elif re.search(f"#define {VAR_REGEX} ", cnt) != None and cnt.find("//") == -1: # Macros
            s = Symbol(words[1])
        else: # Other, we *assume* they are used
            s = Symbol(index) # Random name, so it doesn't conflict
            s.isUsed = True
        s.content.append(content[i])
        s.index = index
        index += 1
        if dontoptimize:
            s.isUsed = True
        if s.name not in symbols:
            symbols[s.name] = []
        symbols[s.name].append(s)
        i += 1

    return symbols

def mark_used_symbols(symbols: [Symbol], fn: Symbol):
    if fn.isUsed:
        return

    for line in fn.content[(1 if fn.isFunction else 0):]: # Skip the definition
        matches = re.findall(VAR_REGEX, line)
        for match in matches:
            if match in symbols:
                for symbol in symbols[match]:
                    if symbol != fn:
                        mark_used_symbols(symbols, symbol)
                    symbol.isUsed = True


def join_from_symbols(symbols: {}) -> [str]:
    # Put the symbols in a list sorted by their index
    s_list = []
    for key, value in symbols.items():
        for symbol in value:
            s_list.append(symbol)
    s_list = sorted(s_list, key=lambda x: x.index)

    # Keep comments that belong to functions (they might be credit)
    for i in range(len(s_list)):
        if s_list[i].isFunction and s_list[i].isUsed:
            j = i-1
            while j > 0:
                if not s_list[j].content[0].strip().startswith("//"):
                    break
                s_list[j].isUsed = True
                j -= 1

    # Extract the content from symbols
    content = []
    for symbol in s_list:
        if symbol.isUsed:
            for line in symbol.content:
                content.append(line)
    return content

def remove_unused_symbols(content: [str], filename) -> [str]:
    symbols = split_to_symbols(content)

    if filename == "voxy_terrain.glsl" or filename == "voxy_water.glsl":
        main_func = "voxy_emitFragment"
    else:
        main_func = "main"
    mark_used_symbols(symbols, symbols[main_func][0])
    symbols[main_func][0].isUsed = True

    return join_from_symbols(symbols)

def optimize_file(path: Path):
    if path.name == "voxy.json": # TODO: Handle this so then we can delete lib/ and global/
        return

    file_mod = resolve_imports(path)

    file_mod = process_macros(file_mod)
    file_mod = remove_unused_symbols(file_mod, path.name)

    file_merged = "\n".join(file_mod)

    write_file_content(file_merged, path)

def optimize():
    os.chdir("release-temp")
    path = Path("shaders/program/")

    for file in path.iterdir():
        if file.is_file():
            optimize_file(file)

    os.chdir("..")

def check_prerequisites():
    if len(argv) != 2:
        print("You need to specify one argument: the shader version")
        exit(1)
    if not isdir("shaders/"):
        print("There are files missing. Are you in the correct path? CWD must be the shader's root.")
        exit(1)

def clone_source():
    os.mkdir("release-temp")
    copytree("shaders/", "release-temp/shaders")
    copy("LICENSE", "release-temp/LICENSE")
    copy("LICENSE-APACHE", "release-temp/LICENSE-APACHE")
    copy("read before you edit.txt", "release-temp/read before you edit.txt")
    copy("PAIN-NOTICE.txt", "release-temp/PAIN-NOTICE.txt")

def zip_up():
    file_name = f"Pain Shaders v{argv[1]}"
    make_archive(file_name, 'zip', "release-temp/")

    # Move into shaderpacks folder
    file_name += ".zip"
    if isfile("../" + file_name):
        os.remove("../" + file_name)
    move(file_name, "../")

def clean_up():
    rmtree("release-temp")

if __name__ == "__main__":
    check_prerequisites()
    clone_source()
    optimize()
    zip_up()
    clean_up()
    