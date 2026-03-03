import { useEffect, useState } from 'react'
import reactLogo from './assets/react.svg'
import viteLogo from '/vite.svg'
import './App.css'

interface Event {
  id: number
  title: string
  description: string
}

function App() {
  const [count, setCount] = useState(0)
  const [hello, setHello] = useState("Loading...");
  const [events, setEvents] = useState<Event[]>([])

  useEffect(() => {
    fetch("/api/hello")
      .then(res => {
        if(!res.ok) {
          throw new Error(`HTTP error: ${res.status}`)
        }

        return res.text()
      })
      .then(data => setHello(data))
      .catch(err => console.error("Backend error:", err.message));
  }, []);

  useEffect(() => {
    fetch("/api/events")
      .then(res => {
        if(!res.ok) {
          throw new Error(`HTTP error: ${res.status}`)
        }

        return res.json()
      })
      .then(data => setEvents(data))
      .catch(err => console.error("Backend error:", err.message));
  }, [])

  return (
    <>
      <div>
        <a href="https://vite.dev" target="_blank">
          <img src={viteLogo} className="logo" alt="Vite logo" />
        </a>
        <a href="https://react.dev" target="_blank">
          <img src={reactLogo} className="logo react" alt="React logo" />
        </a>
      </div>
      <h1>UNIGE Events Vite + React</h1>
      <p>Call from backend: {hello}</p>
      <p>Events size: {events.length}</p>
      
      <div>
        {events.map(event => {
          return <div key={event.id}>
            <h1>{event.title}</h1>
            <p>{event.description}</p>
          </div>
        })}
      </div>
      

      <div className="card">
        <button onClick={() => setCount((count) => count + 1)}>
          count is {count}
        </button>
        <p>
          Edit <code>src/App.tsx</code> and save to test HMR
        </p>
      </div>
      <p className="read-the-docs">
        Click on the Vite and React logos to learn more
      </p>
    </>
  )
}

export default App
