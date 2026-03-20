import { ref, onMounted, onUnmounted } from 'vue'

const getWidth = () => (typeof window !== 'undefined' ? window.innerWidth : 1024)

export function useDevice() {
    const isMobile = ref(getWidth() <= 768)
    const width = ref(getWidth())

    const checkDevice = () => {
        if (typeof window === 'undefined') return
        width.value = window.innerWidth
        isMobile.value = width.value <= 768
    }

    onMounted(() => {
        checkDevice()
        if (typeof window !== 'undefined') window.addEventListener('resize', checkDevice)
    })

    onUnmounted(() => {
        if (typeof window !== 'undefined') window.removeEventListener('resize', checkDevice)
    })

    return {
        isMobile,
        width
    }
}
