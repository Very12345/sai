cmake_minimum_required(VERSION 3.22.1)
project(phoneagent_pty C)

add_library(phoneagent_pty SHARED phoneagent_pty.c)
target_compile_options(phoneagent_pty PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_link_libraries(phoneagent_pty PRIVATE log)
