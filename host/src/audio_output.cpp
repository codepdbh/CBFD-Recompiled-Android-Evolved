// SDL2 sound output for the RT64 build.
//
// The game's audio thread hands over each buffer the audio microcode mixed
// (osAiSetNextBuffer) and paces itself by how much is still queued
// (osAiGetLength), so the SDL device is used in queue mode: the reported
// remaining frames are what SDL hasn't played yet.
//
// The device is opened once, at a fixed rate, and an SDL_AudioStream converts from
// the game's rate, which it changes now and then (a cutscene, for one): reopening the
// device each time took long enough on Android that a cutscene's sound came in late.

#include <cstdio>
#include <mutex>
#include <vector>

#include <SDL.h>

#include "recompui/config.h"

#include "conker.hpp"

namespace {
    constexpr int channels = 2;
    constexpr int output_rate = 48000;

    std::mutex audio_mutex;
    SDL_AudioDeviceID device = 0;
    SDL_AudioStream* stream = nullptr;
    uint32_t game_rate = 0;
    std::vector<float> convert_buffer;
    std::vector<float> output_buffer;

    void open_device() {
        if (device != 0) {
            return;
        }
#if defined(__ANDROID__)
        // AAudio: Android's low-latency output (SDL would otherwise pick OpenSL ES).
        SDL_SetHint(SDL_HINT_AUDIODRIVER, "aaudio,openslES");
#endif
        if (!SDL_WasInit(SDL_INIT_AUDIO) && SDL_InitSubSystem(SDL_INIT_AUDIO) != 0) {
            std::fprintf(stderr, "[audio] SDL audio init failed: %s\n", SDL_GetError());
            return;
        }
        SDL_AudioSpec want{};
        want.freq = output_rate;
        want.format = AUDIO_F32SYS;
        want.channels = channels;
        want.samples = 512; // ~11 ms at 48 kHz: SDL pulls from the queue in chunks this size
        // No allowed changes: SDL converts to whatever the output device uses.
        device = SDL_OpenAudioDevice(nullptr, 0, &want, nullptr, 0);
        if (device == 0) {
            std::fprintf(stderr, "[audio] SDL_OpenAudioDevice failed: %s\n", SDL_GetError());
            return;
        }
        std::printf("[audio] output: %s at %d Hz\n", SDL_GetCurrentAudioDriver(), output_rate);
        SDL_PauseAudioDevice(device, 0);
    }
}

void conker::audio::set_frequency(uint32_t frequency) {
    std::lock_guard lock{audio_mutex};
    open_device();
    if (frequency == game_rate && stream != nullptr) {
        return;
    }
    // Only the conversion changes; what's already queued plays out.
    if (stream != nullptr) {
        SDL_FreeAudioStream(stream);
    }
    game_rate = frequency;
    stream = SDL_NewAudioStream(AUDIO_F32SYS, channels, (int)frequency, AUDIO_F32SYS, channels, output_rate);
    if (stream == nullptr) {
        std::fprintf(stderr, "[audio] SDL_NewAudioStream failed: %s\n", SDL_GetError());
    }
}

void conker::audio::queue_samples(int16_t* samples, size_t sample_count) {
    std::lock_guard lock{audio_mutex};
    if (device == 0 || stream == nullptr) {
        return;
    }
    // RDRAM is kept as native-endian 32-bit words, so each stereo frame's two 16-bit
    // samples read back swapped: [i + 1] is the left channel and [i] the right.
    // Scaled by the main volume from the Sound settings (0-100).
    const float scale = static_cast<float>(recompui::config::sound::get_main_volume()) / (100.0f * 32768.0f);
    convert_buffer.resize(sample_count);
    for (size_t i = 0; i + 1 < sample_count; i += channels) {
        convert_buffer[i + 0] = samples[i + 1] * scale;
        convert_buffer[i + 1] = samples[i + 0] * scale;
    }
    SDL_AudioStreamPut(stream, convert_buffer.data(), (int)(sample_count * sizeof(float)));
    int available = SDL_AudioStreamAvailable(stream);
    if (available > 0) {
        output_buffer.resize((size_t)available / sizeof(float));
        int got = SDL_AudioStreamGet(stream, output_buffer.data(), available);
        if (got > 0) {
            SDL_QueueAudio(device, output_buffer.data(), (Uint32)got);
        }
    }
}

size_t conker::audio::get_frames_remaining() {
    std::lock_guard lock{audio_mutex};
    if (device == 0 || game_rate == 0) {
        return 0;
    }
    // On hardware AI_LEN only counts what is left of the buffer playing now, not the
    // one queued behind it. Conker's audio thread (func_100095A0) makes its next
    // buffer short (552 frames instead of 736) whenever 249 or more samples are
    // left, so reporting the whole SDL queue makes it underrun and pop. Leave one
    // buffer out, so the queue settles at about a buffer and a half.
    // The queue is at the output rate: count it in the game's frames.
    constexpr uint32_t one_buffer = 736;
    uint64_t queued_output = SDL_GetQueuedAudioSize(device) / (channels * sizeof(float));
    uint32_t queued = (uint32_t)(queued_output * game_rate / output_rate);
    return queued > one_buffer ? queued - one_buffer : 0;
}
