find_program(CCACHE ccache)
if(CCACHE)
  message(STATUS "Using ccache: ${CCACHE}")
  set(CMAKE_C_COMPILER_LAUNCHER ${CCACHE})
  set(CMAKE_CXX_COMPILER_LAUNCHER ${CCACHE})
  # Force compiler check to use content hash instead of timestamps
  # This is important for CI environments where files are freshly checked out each time
  execute_process(COMMAND ${CCACHE} --set-config=compiler_check=content)
  # Set ccache stats
  execute_process(COMMAND ${CCACHE} --show-stats)
else()
  message(WARNING "ccache not found, builds may be slower")
endif()