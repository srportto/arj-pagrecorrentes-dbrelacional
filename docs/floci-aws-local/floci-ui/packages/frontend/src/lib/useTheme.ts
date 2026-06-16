import {useEffect, useState} from 'react'

type Theme = 'dark' | 'light'

function getStored(): Theme {
    const v = localStorage.getItem('floci-theme')
    return v === 'light' ? 'light' : 'dark'
}

export function useTheme() {
    const [theme, setTheme] = useState<Theme>(getStored)

    useEffect(() => {
        document.documentElement.setAttribute('data-theme', theme)
        localStorage.setItem('floci-theme', theme)
    }, [theme])

    const toggle = () => setTheme(t => t === 'dark' ? 'light' : 'dark')

    return {theme, toggle}
}
