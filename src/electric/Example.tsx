import * as FileSystem from 'expo-file-system';
import * as SQLite from 'expo-sqlite';


import React, { useEffect, useState } from 'react'

import { makeElectricContext, useLiveQuery } from 'electric-sql/dist/frameworks/react'
import { genUUID, uniqueTabId } from 'electric-sql/dist/util'
import { electrify } from 'electric-sql/dist/drivers/expo-sqlite'


import { authToken } from './auth'
import { Electric, Items as Item, schema } from './generated/client'

// import './Example.css'

const { ElectricProvider, useElectric } = makeElectricContext<Electric>()

async function openCopyOfExistingOnDeviceDatabase(dbName: string): Promise<SQLite.SQLiteDatabase> {
  const dirInfo = await FileSystem.getInfoAsync(FileSystem.documentDirectory + 'SQLite');
  console.log(dirInfo)
  if (!dirInfo.exists) {
    await FileSystem.makeDirectoryAsync(FileSystem.documentDirectory + 'SQLite');
  }

  const fileName = `${dbName}_copy.db`;

  await FileSystem.copyAsync({ from: FileSystem.documentDirectory?.replace("files", "databases") + `${dbName}` , to: FileSystem.documentDirectory + `SQLite/${fileName}` })
  return SQLite.openDatabase(fileName);
}


export const Example = () => {
  const [ electric, setElectric ] = useState<Electric>()

  useEffect(() => {
    let isMounted = true

    const init = async () => {
      const config = {
        auth: {
          token: authToken()
        },
        debug: true,
        url: "http://172.21.60.143:5133" // TODO: obviously this should be an env var
      }

      const { tabId } = uniqueTabId()
      const tabScopedDbName = `electric-${tabId}.db`

      const conn = await openCopyOfExistingOnDeviceDatabase('Events')
      const electric = await electrify(conn, schema, config)

      if (!isMounted) {
        return
      }

      setElectric(electric)
    }

    init()

    return () => {
      isMounted = false
    }
  }, [])

  if (electric === undefined) {
    return null
  }

  return (
    <ElectricProvider db={electric}>
      <ExampleComponent />
    </ElectricProvider>
  )
}

const ExampleComponent = () => {
  const { db } = useElectric()!
  const { results } = useLiveQuery(
    db.items.liveMany()
  )

  useEffect(() => {
    const syncItems = async () => {
      // Resolves when the shape subscription has been established.
      const shape = await db.items.sync()

      // Resolves when the data has been synced into the local database.
      await shape.synced
    }

    syncItems()
  }, [])

  const addItem = async () => {
    // await db.items.create({
    //   data: {
    //     value: genUUID(),
    //   }
    // })
  }

  const clearItems = async () => {
    await db.items.deleteMany()
  }

  const items: Item[] = results ?? []

  return (
    <div>
      <div className="controls">
        <button className="button" onClick={ addItem }>
          Add
        </button>
        <button className="button" onClick={ clearItems }>
          Clear
        </button>
      </div>
      {items.map((item: Item, index: number) => (
        <p key={ index } className="item">
          <code>{ item.value }</code>
        </p>
      ))}
    </div>
  )
}
