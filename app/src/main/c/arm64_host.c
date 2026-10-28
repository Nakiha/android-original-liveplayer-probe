/* Original ABI marker. Packaging this owned library makes PackageManager choose
 * ARM64 Native Bridge for an emulator host that will import ARM64-only code.
 * No third-party decoder or implementation is linked into this library. */
#if !defined(__aarch64__)
#error This target must be built for AArch64
#endif
__attribute__((visibility("default"))) int probe_host_pointer_bits(void) {
    return (int)(sizeof(void *) * 8);
}
