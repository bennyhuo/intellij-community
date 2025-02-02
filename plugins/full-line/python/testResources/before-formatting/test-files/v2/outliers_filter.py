import abc
import json
import math
import os.path
from typing import Iterator, Tuple, List, Type, Optional

from data_processing.datapoint import CodeDataPoint
from data_processing.processors.abstract_processor import ProcessorBase, TrainableProcessorBase
from data_processing.processors.autogenerated_regex_filter import AutogeneratedRegexFilterBase
from data_processing.processors.empty_file_filter import EmptyFileFilterBase
from data_processing.processors.indent_remover import IndentRemoverBase
from data_processing.processors.license_filter import LicenseFilterBase


class OutliersFilterBase(TrainableProcessorBase):
    def __init__(self, dir_path: str):
        super().__init__(dir_path)
        self._file_len: Tuple[int, int] = (0, 1000000000)
        self._maxline_len: Tuple[int, int] = (0, 1000000000)

    def _filter(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return (
            dp
            if self._file_len[0] < len(dp.content) < self._file_len[1]
            and self._maxline_len[0] < max(len(line) for line in dp.content.split("\n")) < self._maxline_len[1]
            else None
        )

    @staticmethod
    def _train(
        dps: Iterator[CodeDataPoint],
        save_path: str,
        file_len_percentiles: Tuple[int, int],
        max_line_len_percentiles: Tuple[int, int],
    ) -> None:
        assert len(file_len_percentiles) == 2
        assert len(max_line_len_percentiles) == 2

        file_lens = []
        maxline_lens = []
        for dp in dps:
            file_lens.append(len(dp.content))
            maxline_lens.append(max(len(line) for line in dp.content.split("\n")))

        file_lens = sorted(file_lens)
        maxline_lens = sorted(maxline_lens)

        file_len = OutliersFilterBase._get_percentiles(file_lens, file_len_percentiles)
        maxline_len = OutliersFilterBase._get_percentiles(maxline_lens, max_line_len_percentiles)

        with open(save_path, "wt") as f:
            json.dump({"file_len": file_len, "maxline_len": maxline_len}, f)

    @staticmethod
    def _get_percentiles(array: List[int], percs: Tuple[int, int]) -> Tuple[int, int]:
        return (
            array[int(math.ceil((len(array) * percs[0]) / 100)) - 1],
            array[int(math.ceil((len(array) * percs[1]) / 100)) - 1],
        )

    def _load(self, path: str) -> None:
        with open(path, "rt") as f:
            d = json.load(f)
        self._file_len = d["file_len"]
        self._maxline_len = d["maxline_len"]

    @staticmethod
    def get_path(dir_path: str) -> str:
        return os.path.join(dir_path, f"outliers.json")

    @staticmethod
    def requires_data_split() -> bool:
        return True

    @abc.abstractmethod
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        raise NotImplementedError

    @staticmethod
    def is_deterministic() -> bool:
        return True

    @staticmethod
    def is_slow() -> bool:
        return False

    @staticmethod
    def must_be_after() -> List[Type[ProcessorBase]]:
        # Basically, after all filters
        return [LicenseFilterBase, AutogeneratedRegexFilterBase, EmptyFileFilterBase]

    @staticmethod
    def must_be_before() -> List[Type[ProcessorBase]]:
        # IndentRemover changes \n behaviour
        return [IndentRemoverBase]


class OutliersFilterTrainData(OutliersFilterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return self._filter(dp)


class OutliersFilterTrainModel(OutliersFilterBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return self._filter(dp)


class OutliersFilterInference(OutliersFilterBase):
    def __init__(self, dir_path: str):
        # We don't use filter in this mode, so we don't load it
        pass

    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return dp
