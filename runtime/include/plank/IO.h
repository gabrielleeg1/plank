#ifndef PLANK_RUNTIME_SRC_IO_H_
#define PLANK_RUNTIME_SRC_IO_H_

extern "C" {
void io_println(char *message);

void io_print(char *message);

char* io_toString(int i);

char* io_toStringPtr(int* i);
};

#endif //PLANK_RUNTIME_SRC_IO_H_
