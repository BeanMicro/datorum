# Clone including all submodules
git clone --recurse-submodules git@github.com:BeanMicro/datorum.git

cd datorum

# If already cloned, initialize and update submodules
git submodule update --init --recursive

# On subsequent pulls, update submodules as well
git pull --recurse-submodules