import { createApp, h } from 'vue'
import ToastComponent from './Toast.vue'

let instance = null

export const toast = (options) => {
    if (instance) {
        instance.unmount()
        document.body.removeChild(instance._container)
    }

    const message = typeof options === 'string' ? options : options.message
    const type = options.type || 'info'
    const duration = options.duration || 3000

    const container = document.createElement('div')
    document.body.appendChild(container)

    instance = createApp({
        render() {
            return h(ToastComponent, {
                message, type, duration, onClosed: () => {
                    if (instance) {
                        instance.unmount()
                        if (container.parentNode) {
                            container.parentNode.removeChild(container)
                        }
                        instance = null
                    }
                }
            })
        }
    })

    instance.mount(container)
    instance._container = container
}

export const message = {
    success: (msg) => toast({ message: msg, type: 'success' }),
    error: (msg) => toast({ message: msg, type: 'error' }),
    info: (msg) => toast({ message: msg, type: 'info' }),
    warning: (msg) => toast({ message: msg, type: 'warning' })
}
